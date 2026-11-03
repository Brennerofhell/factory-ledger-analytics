package dev.flanalytics.history;

import dev.flanalytics.monitoring.DeltaTracker;
import dev.flanalytics.monitoring.MeasurementSample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Multi-resolution history of one measurement point: 1 min buckets (6 h), 10 min buckets (7 d),
 * 1 h buckets (up to the retention). Older data is aggregated, never kept raw.
 * All methods are synchronized; the storage thread reads via {@link #snapshotForSave()}.
 */
public final class PointHistory {
    public static final long[] WIDTH = {60_000L, 600_000L, 3_600_000L};
    private static final long[] KEEP = {6 * 3_600_000L, 7 * 86_400_000L, Long.MAX_VALUE};
    private static final long DAY = 86_400_000L;

    public final String id;
    public String dimension = "";
    public int x, y, z;
    public String mode = "";
    public long lastSampleMillis;
    @SuppressWarnings("unchecked")
    final TreeMap<Long, Bucket>[] tiers = new TreeMap[]{new TreeMap<>(), new TreeMap<>(), new TreeMap<>()};
    private volatile boolean dirty;

    public PointHistory(String id) {
        this.id = id;
    }

    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }
    public void markDirty() { dirty = true; }

    public synchronized void add(MeasurementSample s) {
        dimension = s.dimension();
        x = s.x(); y = s.y(); z = s.z();
        mode = s.mode().name();
        long intervalMs = s.intervalTicks() * 50L;
        Bucket b = bucketFor(s.wallMillis());
        // Time since the previous sample beyond the normal interval = nobody measured (restart, unloaded chunk).
        if (lastSampleMillis > 0 && s.wallMillis() - lastSampleMillis > intervalMs * 3) {
            b.gapMs += s.wallMillis() - lastSampleMillis - intervalMs;
        } else if (s.gapBefore()) {
            b.gapMs += intervalMs;
        }
        lastSampleMillis = s.wallMillis();
        if (!s.baseline()) {
            b.samples++;
            b.coveredMs += intervalMs;
            for (Map.Entry<String, DeltaTracker.Delta> d : s.deltas().entrySet()) {
                long[] a = b.item(d.getKey());
                a[0] += d.getValue().in();
                a[1] += d.getValue().out();
                if (!s.stock().containsKey(d.getKey())) a[2] = 0;
            }
        }
        for (Map.Entry<String, Long> e : s.stock().entrySet()) b.item(e.getKey())[2] = e.getValue();
        dirty = true;
    }

    private Bucket bucketFor(long wall) {
        long start = wall - Math.floorMod(wall, WIDTH[0]);
        return tiers[0].computeIfAbsent(start, k -> new Bucket(k, WIDTH[0]));
    }

    /** Aggregate old buckets into coarser tiers and drop everything past the retention. */
    public synchronized void compact(long now, int retentionDays) {
        for (int t = 0; t < 2; t++) {
            long cutoff = now - KEEP[t];
            var it = tiers[t].entrySet().iterator();
            while (it.hasNext()) {
                Bucket b = it.next().getValue();
                if (b.start + b.width > cutoff) break; // sorted ascending
                long ns = b.start - Math.floorMod(b.start, WIDTH[t + 1]);
                final long nextWidth = WIDTH[t + 1];
                tiers[t + 1].computeIfAbsent(ns, k -> new Bucket(k, nextWidth)).mergeLater(b);
                it.remove();
                dirty = true;
            }
        }
        long oldest = now - retentionDays * DAY;
        for (TreeMap<Long, Bucket> tier : tiers) { // the retention is the maximum age of ANY stored data
            if (!tier.headMap(oldest, false).isEmpty()) {
                tier.headMap(oldest, false).clear();
                dirty = true;
            }
        }
    }

    /**
     * Drop the oldest ~10% of the coarsest non-empty tier (used when the size budget is exceeded):
     * hourly buckets first, then 10 min, then 1 min, so a young history can be trimmed too.
     */
    public synchronized void trimOldest() {
        for (int t = 2; t >= 0; t--) {
            if (tiers[t].isEmpty()) continue;
            int drop = Math.max(1, tiers[t].size() / 10);
            for (int i = 0; i < drop && !tiers[t].isEmpty(); i++) tiers[t].pollFirstEntry();
            dirty = true;
            return;
        }
    }

    /** Read-only view of the three tiers (call only on snapshots / single-threaded contexts). */
    public List<java.util.Collection<Bucket>> tiersView() {
        List<java.util.Collection<Bucket>> v = new ArrayList<>(3);
        for (TreeMap<Long, Bucket> t : tiers) v.add(t.values());
        return v;
    }

    public synchronized void putBucket(int tier, Bucket b) {
        tiers[tier].put(b.start, b);
    }

    public synchronized int bucketCount() {
        return tiers[0].size() + tiers[1].size() + tiers[2].size();
    }

    /** Copies of all buckets in [from,to], merged down to at most {@code maxBuckets} entries. */
    public synchronized List<Bucket> query(long from, long to, int maxBuckets) {
        List<Bucket> all = new ArrayList<>();
        for (TreeMap<Long, Bucket> t : tiers) {
            for (Bucket b : t.subMap(from - WIDTH[2], true, to, true).values()) {
                if (b.start + b.width > from) all.add(b.copyAs(b.start, b.width));
            }
        }
        return downsample(all, from, to, maxBuckets);
    }

    /** Merge buckets into at most about {@code maxBuckets} equal slots across [from,to]; totals are preserved. */
    public static List<Bucket> downsample(List<Bucket> all, long from, long to, int maxBuckets) {
        all = new ArrayList<>(all);
        all.sort((a, b) -> Long.compare(a.start, b.start));
        if (maxBuckets <= 0 || all.size() <= maxBuckets) return all;
        long span = Math.max(1, to - from);
        long step = (span + maxBuckets - 1) / maxBuckets;
        TreeMap<Long, Bucket> merged = new TreeMap<>();
        for (Bucket b : all) {
            long ns = from + Math.floorDiv(Math.max(0, b.start - from), step) * step;
            merged.computeIfAbsent(ns, k -> new Bucket(k, step)).mergeLater(b);
        }
        return new ArrayList<>(merged.values());
    }

    /** Consistent copy of the state for the storage thread. */
    public synchronized PointHistory snapshotForSave() {
        PointHistory c = new PointHistory(id);
        c.dimension = dimension; c.x = x; c.y = y; c.z = z; c.mode = mode; c.lastSampleMillis = lastSampleMillis;
        for (int t = 0; t < 3; t++) {
            for (Map.Entry<Long, Bucket> e : tiers[t].entrySet()) {
                c.tiers[t].put(e.getKey(), e.getValue().copyAs(e.getValue().start, e.getValue().width));
            }
        }
        return c;
    }
}
