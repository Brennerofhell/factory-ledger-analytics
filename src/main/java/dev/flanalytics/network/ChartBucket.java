package dev.flanalytics.network;

/** One time slot as sent to the client. item/in/out/stock are parallel arrays; item[] indexes the response's item list. */
public record ChartBucket(long start, long width, long coveredMs, long gapMs, int[] item, long[] in, long[] out, long[] stock) {
    public long end() { return start + width; }
}
