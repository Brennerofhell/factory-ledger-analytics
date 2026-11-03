package dev.flanalytics.monitoring;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks the stock of one measurement point and reports changes between samples.
 * NOTE: these are STOCK deltas, not gross throughput. Simultaneous in- and outflow within one
 * sampling interval cancels out. Never across a gap: after {@link #markGap()} the next sample
 * only re-establishes the baseline.
 */
public final class DeltaTracker {
    public record Delta(long in, long out) {
        public long net() { return in - out; }
    }

    public record Result(Map<String, Long> stock, Map<String, Delta> deltas, boolean gapBefore, boolean baseline) {}

    private Map<String, Long> previous;
    private boolean started;
    private boolean gapPending;

    public void markGap() {
        if (started) gapPending = true;
        previous = null;
    }

    public Result update(Map<String, Long> current) {
        Map<String, Long> cur = new HashMap<>(current);
        if (previous == null) {
            boolean gap = gapPending;
            gapPending = false;
            started = true;
            previous = cur;
            return new Result(cur, Map.of(), gap, true);
        }
        Map<String, Delta> deltas = new HashMap<>();
        for (Map.Entry<String, Long> e : cur.entrySet()) {
            long d = e.getValue() - previous.getOrDefault(e.getKey(), 0L);
            if (d > 0) deltas.put(e.getKey(), new Delta(d, 0));
            else if (d < 0) deltas.put(e.getKey(), new Delta(0, -d));
        }
        for (Map.Entry<String, Long> e : previous.entrySet()) {
            if (!cur.containsKey(e.getKey()) && e.getValue() > 0) deltas.put(e.getKey(), new Delta(0, e.getValue()));
        }
        previous = cur;
        return new Result(cur, deltas, false, false);
    }
}
