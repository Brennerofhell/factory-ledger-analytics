package dev.flanalytics.client.charts;

import dev.flanalytics.client.charts.ChartModel.Metric;
import dev.flanalytics.client.charts.ChartModel.Series;
import dev.flanalytics.client.charts.ChartModel.Unit;
import dev.flanalytics.network.ChartBucket;
import dev.flanalytics.network.HistoryResponsePayload;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Draws line / bar / comparison / cumulative / in-out charts with plain GuiGraphics fills (no WebView). */
public final class ChartWidget {
    public enum Type { LINE, BAR, COMPARE, CUMULATIVE, IN_OUT }

    public static final int[] PALETTE = {0xFFE69F00, 0xFF56B4E9, 0xFF009E73, 0xFFF0E442, 0xFF0072B2, 0xFFD55E00, 0xFFCC79A7, 0xFFFFFFFF};
    private static final int COL_GRID = 0x40FFFFFF, COL_GAP = 0x55FF3333, COL_BG = 0xA0000000;

    public int x, y, w, h;
    public String itemName(String id) { return id; }

    public static int color(int itemIdx) { return PALETTE[itemIdx % PALETTE.length]; }

    public static String time(long ms, long span) {
        var z = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault());
        return z.format(DateTimeFormatter.ofPattern(span <= 3_600_000L ? "HH:mm:ss" : span <= 2 * 86_400_000L ? "HH:mm" : "dd.MM HH:mm"));
    }

    public void render(GuiGraphics g, Font font, HistoryResponsePayload d, Type type, Metric metric, Unit unit,
                       Set<String> hidden, long vFrom, long vTo, int mx, int my, double[] div, java.util.function.IntFunction<String> labels) {
        g.fill(x, y, x + w, y + h, COL_BG);
        List<ChartBucket> buckets = d.buckets();
        List<String> items = d.items();
        long span = Math.max(1, vTo - vFrom);

        List<Series> series = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        boolean amountAxis = type == Type.CUMULATIVE;
        if (type == Type.IN_OUT) {
            for (int i = 0; i < items.size(); i++) {
                if (hidden.contains(items.get(i))) continue;
                series.add(ChartModel.scaled(ChartModel.rates(buckets, items, i, Metric.IN, unit), div[i])); colors.add(0xFF55FF55);
                series.add(ChartModel.scaled(ChartModel.rates(buckets, items, i, Metric.OUT, unit), div[i])); colors.add(0xFFFF5555);
                break; // in/out comparison is shown for the first visible item to stay readable
            }
        } else {
            for (int i = 0; i < items.size(); i++) {
                if (hidden.contains(items.get(i))) continue;
                series.add(ChartModel.scaled(type == Type.CUMULATIVE ? ChartModel.cumulative(buckets, items, i) : ChartModel.rates(buckets, items, i, metric, unit), div[i]));
                colors.add(color(i));
            }
        }

        double min = 0, max = 0;
        boolean any = false;
        for (Series s : series) for (int i = 0; i < s.v().length; i++) {
            double t = s.t()[i];
            if (Double.isNaN(s.v()[i]) || t < vFrom || t > vTo) continue;
            min = Math.min(min, s.v()[i]); max = Math.max(max, s.v()[i]); any = true;
        }
        if (!any) { min = 0; max = 1; }
        double pad = (max - min) * 0.08;
        double[] ticks = ChartModel.niceTicks(min, max + pad, 5);
        double lo = Math.min(min, ticks[0]), hi = Math.max(max + pad, ticks[ticks.length - 1]);
        if (hi <= lo) hi = lo + 1;

        int left = x + 42, top = y + 6, pw = w - 48, ph = h - 24;
        g.enableScissor(left, top, left + pw, top + ph);
        for (double t : ticks) {
            int py = ypx(t, lo, hi, top, ph);
            g.fill(left, py, left + pw, py + 1, t == 0 ? 0x90FFFFFF : COL_GRID);
        }
        for (ChartBucket b : buckets) { // measurement gaps are marked explicitly, never drawn as zero
            if (b.coveredMs() > 0 && b.gapMs() == 0) continue;
            int x1 = xpx(b.start(), vFrom, span, left, pw), x2 = Math.max(x1 + 1, xpx(b.end(), vFrom, span, left, pw));
            g.fill(Math.max(left, x1), top, Math.min(left + pw, x2), top + ph, COL_GAP);
        }
        int zero = ypx(0, lo, hi, top, ph);
        int groupCount = Math.max(1, series.size());
        for (int si = 0; si < series.size(); si++) {
            Series s = series.get(si);
            int col = colors.get(si);
            boolean bars = type == Type.BAR || type == Type.COMPARE;
            for (int i = 0; i < s.v().length; i++) {
                if (Double.isNaN(s.v()[i])) continue;
                int px = xpx((long) s.t()[i], vFrom, span, left, pw), py = ypx(s.v()[i], lo, hi, top, ph);
                if (bars) {
                    ChartBucket b = buckets.get(i);
                    int bx1 = xpx(b.start(), vFrom, span, left, pw), bx2 = xpx(b.end(), vFrom, span, left, pw);
                    int bw = Math.max(1, bx2 - bx1 - 1);
                    int sw = type == Type.COMPARE ? Math.max(1, bw / groupCount) : bw;
                    int bx = bx1 + (type == Type.COMPARE ? si * sw : 0);
                    g.fill(bx, Math.min(py, zero), bx + sw, Math.max(py, zero) + 1, col);
                } else if (i + 1 < s.v().length && !Double.isNaN(s.v()[i + 1])) {
                    line(g, px, py, xpx((long) s.t()[i + 1], vFrom, span, left, pw), ypx(s.v()[i + 1], lo, hi, top, ph), col);
                } else {
                    g.fill(px - 1, py - 1, px + 2, py + 2, col);
                }
            }
        }
        // hover marker
        ChartBucket hover = null;
        if (mx >= left && mx < left + pw && my >= top && my < top + ph && !buckets.isEmpty()) {
            long tm = vFrom + (long) ((mx - left) / (double) pw * span);
            for (ChartBucket b : buckets) if (tm >= b.start() && tm < b.end() || hover == null && b.start() > tm) { hover = b; if (tm >= b.start() && tm < b.end()) break; }
            if (hover == null) hover = buckets.get(buckets.size() - 1);
            g.fill(mx, top, mx + 1, top + ph, 0x80FFFFFF);
        }
        g.disableScissor();

        for (double t : ticks) {
            int py = ypx(t, lo, hi, top, ph);
            if (py < top || py > top + ph) continue;
            String s = ChartModel.formatRate(t);
            g.drawString(font, s, left - 4 - font.width(s), py - 4, 0xFFBBBBBB);
        }
        for (int i = 0; i <= 4; i++) {
            long tm = vFrom + span * i / 4;
            String s = time(tm, span);
            int px = left + pw * i / 4 - (i == 4 ? font.width(s) : i == 0 ? 0 : font.width(s) / 2);
            g.drawString(font, s, px, y + h - 12, 0xFFBBBBBB);
        }
        String axis = amountAxis ? "items" : "items" + unit.suffix;
        g.drawString(font, axis, x + 2, y - 10, 0xFFAAAAAA);
        if (buckets.isEmpty()) g.drawCenteredString(font, "No data for this range yet", left + pw / 2, top + ph / 2, 0xFFAAAAAA);

        if (hover != null) tooltip(g, font, d, hover, metric, unit, hidden, mx, my, div, labels);
    }

    private void tooltip(GuiGraphics g, Font font, HistoryResponsePayload d, ChartBucket b, Metric metric, Unit unit,
                         Set<String> hidden, int mx, int my, double[] div, java.util.function.IntFunction<String> labels) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(time(b.start(), 1) + (d.live() ? "" : " (" + (b.width() / 1000) + "s slot)")));
        if (b.coveredMs() <= 0) lines.add(Component.literal("No measurement (gap)").withStyle(s -> s.withColor(0xFF5555)));
        else if (b.gapMs() > 0) lines.add(Component.literal("Gap inside slot: " + b.gapMs() / 1000 + "s").withStyle(s -> s.withColor(0xFF9955)));
        for (int k = 0; k < b.item().length; k++) {
            String id = d.items().get(b.item()[k]);
            if (hidden.contains(id) || b.coveredMs() <= 0) continue;
            int idx = b.item()[k];
            double dv = div[idx];
            double f = (double) unit.ms / b.coveredMs();
            double val = (metric == Metric.IN ? b.in()[k] : metric == Metric.OUT ? b.out()[k] : b.in()[k] - b.out()[k]) * f / dv;
            lines.add(Component.literal(labels.apply(idx) + ": " + ChartModel.formatRate(val) + unit.suffix
                    + "  (+" + ChartModel.formatRate(b.in()[k] / dv) + " / -" + ChartModel.formatRate(b.out()[k] / dv)
                    + ", stock " + ChartModel.formatRate(b.stock()[k] / dv) + ")"));
            if (lines.size() > 12) break;
        }
        g.renderComponentTooltip(font, lines, mx, my);
    }

    private static int xpx(long t, long vFrom, long span, int left, int pw) {
        return left + (int) Math.round((t - vFrom) / (double) span * pw);
    }

    private static int ypx(double v, double lo, double hi, int top, int ph) {
        return top + ph - (int) Math.round((v - lo) / (hi - lo) * ph);
    }

    private static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int col) {
        int dx = x2 - x1, dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) { g.fill(x1, y1, x1 + 1, y1 + 1, col); return; }
        for (int i = 0; i <= steps; i++) {
            int px = x1 + dx * i / steps, py = y1 + dy * i / steps;
            g.fill(px, py, px + 1, py + 2, col);
        }
    }
}
