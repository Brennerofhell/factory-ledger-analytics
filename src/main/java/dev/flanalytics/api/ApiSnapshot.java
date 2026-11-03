package dev.flanalytics.api;

import dev.flanalytics.monitoring.DeltaTracker;
import dev.flanalytics.monitoring.MeasurementSample;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Builds the JSON-serialisable view served by the API and pushed to the website. Pure, no Minecraft types. */
public final class ApiSnapshot {
    public static final int VERSION = 1;
    public static final int MAX_POINTS = 512;
    public static final int MAX_ITEMS_PER_POINT = 64;

    private ApiSnapshot() {}

    /** @param windowMillis only samples newer than {@code nowMillis - windowMillis} count toward the rates */
    public static Map<String, Object> build(List<MeasurementSample> samples, long nowMillis, long windowMillis) {
        Map<String, List<MeasurementSample>> byPoint = new TreeMap<>();
        for (MeasurementSample s : samples) byPoint.computeIfAbsent(s.pointId(), k -> new ArrayList<>()).add(s);

        List<Object> points = new ArrayList<>();
        for (Map.Entry<String, List<MeasurementSample>> e : byPoint.entrySet()) {
            if (points.size() >= MAX_POINTS) break;
            points.add(point(e.getValue(), nowMillis, windowMillis));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", VERSION);
        root.put("generatedAtMillis", nowMillis);
        root.put("windowSeconds", windowMillis / 1000);
        root.put("kind", "STOCK_DELTA");
        root.put("note", "Rates are changes of stored amounts, not gross production. Gaps are not counted.");
        root.put("points", points);
        return root;
    }

    private static Map<String, Object> point(List<MeasurementSample> list, long now, long window) {
        MeasurementSample last = list.get(list.size() - 1);
        Map<String, long[]> sums = new TreeMap<>(); // item -> {in, out}
        long coveredMs = 0;
        int gaps = 0;
        for (MeasurementSample s : list) {
            if (s.wallMillis() < now - window) continue;
            if (s.gapBefore()) gaps++;
            if (s.baseline()) continue; // baseline sample carries no delta
            coveredMs += s.intervalTicks() * 50L;
            for (Map.Entry<String, DeltaTracker.Delta> d : s.deltas().entrySet()) {
                long[] a = sums.computeIfAbsent(d.getKey(), k -> new long[2]);
                a[0] += d.getValue().in();
                a[1] += d.getValue().out();
            }
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", last.pointId());
        p.put("dimension", last.dimension());
        p.put("x", last.x());
        p.put("y", last.y());
        p.put("z", last.z());
        p.put("lastSampleMillis", last.wallMillis());
        p.put("compactingBlock", last.compactingBlock());
        p.put("compactingMode", last.mode().name());
        p.put("gapsInWindow", gaps);
        p.put("coveredSeconds", coveredMs / 1000);
        Map<String, Object> items = new LinkedHashMap<>();
        Map<String, Long> stock = new TreeMap<>(last.stock());
        for (String item : union(stock, sums)) {
            if (items.size() >= MAX_ITEMS_PER_POINT) break;
            long[] a = sums.getOrDefault(item, new long[2]);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stock", stock.getOrDefault(item, 0L));
            m.put("inTotal", a[0]);
            m.put("outTotal", a[1]);
            m.put("netTotal", a[0] - a[1]);
            double perMin = coveredMs > 0 ? 60000.0 / coveredMs : 0;
            m.put("inPerMinute", round(a[0] * perMin));
            m.put("outPerMinute", round(a[1] * perMin));
            m.put("netPerMinute", round((a[0] - a[1]) * perMin));
            items.put(item, m);
        }
        p.put("items", items);
        return p;
    }

    private static Iterable<String> union(Map<String, Long> stock, Map<String, long[]> sums) {
        TreeMap<String, Boolean> k = new TreeMap<>();
        stock.keySet().forEach(i -> k.put(i, true));
        sums.keySet().forEach(i -> k.put(i, true));
        return k.keySet();
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
