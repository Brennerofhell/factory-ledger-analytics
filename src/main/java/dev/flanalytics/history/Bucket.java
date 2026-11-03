package dev.flanalytics.history;

import java.util.Map;
import java.util.TreeMap;

/** Aggregated measurements of one point for one time slot. items: itemId -> {in, out, lastStock}. */
public final class Bucket {
    public final long start;
    public final long width;
    public long coveredMs;
    public long gapMs;
    public int samples;
    public final Map<String, long[]> items = new TreeMap<>();

    public Bucket(long start, long width) {
        this.start = start;
        this.width = width;
    }

    public long[] item(String id) {
        return items.computeIfAbsent(id, k -> new long[3]);
    }

    /** Merge a chronologically LATER (or equal) bucket into this one; its stock values win. */
    public void mergeLater(Bucket o) {
        coveredMs += o.coveredMs;
        gapMs += o.gapMs;
        samples += o.samples;
        for (Map.Entry<String, long[]> e : o.items.entrySet()) {
            long[] a = item(e.getKey());
            a[0] += e.getValue()[0];
            a[1] += e.getValue()[1];
            a[2] = e.getValue()[2];
        }
    }

    public Bucket copyAs(long newStart, long newWidth) {
        Bucket b = new Bucket(newStart, newWidth);
        b.mergeLater(this);
        return b;
    }
}
