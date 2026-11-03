package dev.flanalytics.network;

import dev.flanalytics.history.Bucket;
import dev.flanalytics.history.PointHistory;
import dev.flanalytics.monitoring.DeltaTracker;
import dev.flanalytics.monitoring.MeasurementSample;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Server-side query: validates the range, picks live samples or stored history, limits the amount of data. */
public final class QueryService {
    public static final int MAX_BUCKETS = 600;
    public static final int MAX_ITEMS = 48;
    public static final long MAX_SPAN_MS = 400L * 86_400_000L;
    public static final long LIVE_MAX_SPAN_MS = 5 * 60_000L;

    public record Result(List<String> items, List<ChartBucket> buckets, boolean live, long from, long to) {}

    private QueryService() {}

    /** Max number of points combined into one chart. */
    public static final int MAX_POINTS = 8;
    public static final String POINT_SEPARATOR = "|";

    public static Result query(PointHistory history, List<MeasurementSample> recent, String pointId,
                               long from, long to, int maxBuckets, long now) {
        return queryMulti(id -> history, recent, List.of(pointId), from, to, maxBuckets, now);
    }

    /**
     * One chart from several points (e.g. gold from one source, wood from another). Same items from different
     * points are summed; different items stay separate. Live data is used only if every point has it.
     */
    public static Result queryMulti(java.util.function.Function<String, PointHistory> histories, List<MeasurementSample> recent,
                                    List<String> pointIds, long from, long to, int maxBuckets, long now) {
        to = Math.min(to, now);
        from = Math.max(from, to - MAX_SPAN_MS);
        if (from >= to) from = to - 60_000L;
        int max = Math.max(10, Math.min(maxBuckets, MAX_BUCKETS));
        List<String> ids = pointIds.size() > MAX_POINTS ? pointIds.subList(0, MAX_POINTS) : pointIds;
        List<List<Bucket>> perPoint = new ArrayList<>();
        boolean live = false;
        if (to - from <= LIVE_MAX_SPAN_MS) {
            live = true;
            for (String id : ids) {
                List<Bucket> l = liveBuckets(recent, id, from, to);
                if (l.size() < 2) { live = false; break; }
                perPoint.add(PointHistory.downsample(l, from, to, max));
            }
        }
        if (!live) {
            perPoint.clear();
            for (String id : ids) {
                PointHistory h = histories.apply(id);
                perPoint.add(h == null ? new ArrayList<>() : h.query(from, to, max));
            }
        }
        return toResult(perPoint.size() == 1 ? perPoint.get(0) : mergePoints(perPoint), live, from, to);
    }

    /** Combine bucket lists of several points by start time (slots are aligned: same tier / same sampling grid). */
    public static List<Bucket> mergePoints(List<List<Bucket>> lists) {
        java.util.TreeMap<Long, Bucket> byStart = new java.util.TreeMap<>();
        for (List<Bucket> l : lists) {
            for (Bucket b : l) {
                Bucket t = byStart.get(b.start);
                if (t == null) { byStart.put(b.start, b.copyAs(b.start, b.width)); continue; }
                t.coveredMs = Math.max(t.coveredMs, b.coveredMs);
                t.gapMs = Math.max(t.gapMs, b.gapMs);
                t.samples += b.samples;
                for (Map.Entry<String, long[]> e : b.items.entrySet()) {
                    long[] a = t.item(e.getKey());
                    a[0] += e.getValue()[0]; a[1] += e.getValue()[1]; a[2] += e.getValue()[2];
                }
            }
        }
        return new ArrayList<>(byStart.values());
    }

    static List<Bucket> liveBuckets(List<MeasurementSample> recent, String pointId, long from, long to) {
        List<Bucket> out = new ArrayList<>();
        long prev = -1;
        for (MeasurementSample s : recent) {
            if (!s.pointId().equals(pointId)) continue;
            long interval = s.intervalTicks() * 50L;
            long gap = 0;
            if (prev > 0 && s.wallMillis() - prev > interval * 3) gap = s.wallMillis() - prev - interval;
            else if (s.gapBefore()) gap = interval;
            prev = s.wallMillis();
            if (s.wallMillis() < from || s.wallMillis() > to) continue;
            Bucket b = new Bucket(Math.round((double) s.wallMillis() / interval) * interval - interval, interval);
            b.gapMs = gap;
            if (!s.baseline()) {
                b.samples = 1;
                b.coveredMs = interval;
                for (Map.Entry<String, DeltaTracker.Delta> d : s.deltas().entrySet()) {
                    long[] a = b.item(d.getKey());
                    a[0] = d.getValue().in();
                    a[1] = d.getValue().out();
                }
            }
            for (Map.Entry<String, Long> e : s.stock().entrySet()) b.item(e.getKey())[2] = e.getValue();
            out.add(b);
        }
        return out;
    }

    static Result toResult(List<Bucket> buckets, boolean live, long from, long to) {
        Map<String, Long> activity = new HashMap<>();
        for (Bucket b : buckets) {
            for (Map.Entry<String, long[]> e : b.items.entrySet()) {
                activity.merge(e.getKey(), e.getValue()[0] + e.getValue()[1] + (e.getValue()[2] > 0 ? 1 : 0), Long::sum);
            }
        }
        List<String> items = new ArrayList<>(activity.keySet());
        items.sort(Comparator.<String>comparingLong(activity::get).reversed().thenComparing(Comparator.naturalOrder()));
        if (items.size() > MAX_ITEMS) items = new ArrayList<>(items.subList(0, MAX_ITEMS));
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < items.size(); i++) index.put(items.get(i), i);
        List<ChartBucket> out = new ArrayList<>(buckets.size());
        for (Bucket b : buckets) {
            int n = 0;
            for (String id : b.items.keySet()) if (index.containsKey(id)) n++;
            int[] it = new int[n];
            long[] in = new long[n], ou = new long[n], st = new long[n];
            int k = 0;
            for (Map.Entry<String, long[]> e : b.items.entrySet()) {
                Integer idx = index.get(e.getKey());
                if (idx == null) continue;
                it[k] = idx; in[k] = e.getValue()[0]; ou[k] = e.getValue()[1]; st[k] = e.getValue()[2];
                k++;
            }
            out.add(new ChartBucket(b.start, b.width, b.coveredMs, b.gapMs, it, in, ou, st));
        }
        return new Result(items, out, live, from, to);
    }
}
