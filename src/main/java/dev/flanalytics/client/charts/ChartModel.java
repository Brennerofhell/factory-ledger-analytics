package dev.flanalytics.client.charts;

import dev.flanalytics.network.ChartBucket;
import java.util.ArrayList;
import java.util.List;

/** Pure chart maths (no Minecraft types): rates, cumulative curves, statistics, scaling, zoom and pan. */
public final class ChartModel {
    public enum Metric { NET, IN, OUT }

    public enum Unit {
        PER_MINUTE(60_000L, "/min"), PER_HOUR(3_600_000L, "/h"), PER_DAY(86_400_000L, "/day");
        public final long ms;
        public final String suffix;
        Unit(long ms, String suffix) { this.ms = ms; this.suffix = suffix; }
    }

    /** t = bucket centre in ms, v = value (NaN = no measurement: a gap, never an invented zero). */
    public record Series(String itemId, double[] t, double[] v) {}

    public record Stats(double min, double max, double avg, long totalIn, long totalOut, long coveredMs, long gapMs) {}

    private ChartModel() {}

    private static long amount(ChartBucket b, int slot, Metric m) {
        return switch (m) {
            case IN -> b.in()[slot];
            case OUT -> b.out()[slot];
            case NET -> b.in()[slot] - b.out()[slot];
        };
    }

    private static int slotOf(ChartBucket b, int itemIdx) {
        for (int i = 0; i < b.item().length; i++) if (b.item()[i] == itemIdx) return i;
        return -1;
    }

    public static Series rates(List<ChartBucket> buckets, List<String> items, int itemIdx, Metric m, Unit u) {
        double[] t = new double[buckets.size()], v = new double[buckets.size()];
        for (int i = 0; i < buckets.size(); i++) {
            ChartBucket b = buckets.get(i);
            t[i] = b.start() + b.width() / 2.0;
            if (b.coveredMs() <= 0) { v[i] = Double.NaN; continue; }
            int slot = slotOf(b, itemIdx);
            v[i] = slot < 0 ? 0 : amount(b, slot, m) * (double) u.ms / b.coveredMs();
        }
        return new Series(items.get(itemIdx), t, v);
    }

    /** Running sum of the net change. Gaps add nothing. */
    public static Series cumulative(List<ChartBucket> buckets, List<String> items, int itemIdx) {
        double[] t = new double[buckets.size()], v = new double[buckets.size()];
        double sum = 0;
        for (int i = 0; i < buckets.size(); i++) {
            ChartBucket b = buckets.get(i);
            t[i] = b.start() + b.width() / 2.0;
            int slot = slotOf(b, itemIdx);
            if (slot >= 0 && b.coveredMs() > 0) sum += b.in()[slot] - b.out()[slot];
            v[i] = sum;
        }
        return new Series(items.get(itemIdx), t, v);
    }

    public static Stats stats(List<ChartBucket> buckets, int itemIdx, Metric m, Unit u) {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        long in = 0, out = 0, covered = 0, gap = 0;
        for (ChartBucket b : buckets) {
            gap += b.gapMs();
            if (b.coveredMs() <= 0) continue;
            covered += b.coveredMs();
            int slot = slotOf(b, itemIdx);
            long bi = slot < 0 ? 0 : b.in()[slot], bo = slot < 0 ? 0 : b.out()[slot];
            in += bi; out += bo;
            double r = (switch (m) { case IN -> bi; case OUT -> bo; case NET -> bi - bo; }) * (double) u.ms / b.coveredMs();
            min = Math.min(min, r);
            max = Math.max(max, r);
        }
        if (covered == 0) return new Stats(0, 0, 0, 0, 0, 0, gap);
        long total = switch (m) { case IN -> in; case OUT -> out; case NET -> in - out; };
        return new Stats(min, max, total * (double) u.ms / covered, in, out, covered, gap);
    }

    /** "Nice" axis ticks covering [min,max]. */
    public static double[] niceTicks(double min, double max, int target) {
        if (!(max > min)) { max = min + 1; }
        double raw = (max - min) / Math.max(1, target);
        double pow = Math.pow(10, Math.floor(Math.log10(raw)));
        double f = raw / pow;
        double step = (f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10) * pow;
        double start = Math.floor(min / step) * step;
        List<Double> ticks = new ArrayList<>();
        for (double v = start; v <= max + step * 0.5 && ticks.size() < 64; v += step) ticks.add(Math.round(v / step) * step);
        return ticks.stream().mapToDouble(Double::doubleValue).toArray();
    }

    /** Zoom the visible range around an anchor (0..1 across the plot). factor < 1 zooms in. */
    public static long[] zoom(long from, long to, double anchor, double factor, long minSpan, long maxSpan, long now) {
        long span = to - from;
        long newSpan = Math.max(minSpan, Math.min(maxSpan, Math.round(span * factor)));
        long pivot = from + Math.round(span * anchor);
        long nf = pivot - Math.round(newSpan * anchor);
        long nt = nf + newSpan;
        if (nt > now) { nt = now; nf = nt - newSpan; }
        return new long[]{nf, nt};
    }

    public static long[] pan(long from, long to, long deltaMs, long now) {
        long span = to - from;
        long nt = Math.min(now, to + deltaMs);
        return new long[]{nt - span, nt};
    }

    public static String formatRate(double v) {
        double a = Math.abs(v);
        if (a >= 1_000_000) return String.format("%.2fM", v / 1_000_000);
        if (a >= 10_000) return String.format("%.1fk", v / 1000);
        if (a >= 100) return String.format("%.0f", v);
        if (a >= 1) return String.format("%.1f", v);
        return a == 0 ? "0" : String.format("%.2f", v);
    }
}
