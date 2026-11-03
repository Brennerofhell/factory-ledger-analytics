package dev.flanalytics.client.gui;

import dev.flanalytics.client.ClientHooks;
import dev.flanalytics.client.charts.ChartModel;
import dev.flanalytics.client.charts.ChartModel.Metric;
import dev.flanalytics.client.charts.ChartModel.Unit;
import dev.flanalytics.client.charts.ChartWidget;
import dev.flanalytics.config.AnalyticsConfig;
import dev.flanalytics.network.ChartBucket;
import dev.flanalytics.network.HistoryRequestPayload;
import dev.flanalytics.network.HistoryResponsePayload;
import dev.flanalytics.network.PointMeta;
import dev.flanalytics.network.SetModePayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Separate Analytics screen (the original Factory Ledger screen has fixed tabs and is not extended via mixin). */
public class AnalyticsScreen extends Screen {
    private enum Tab { OVERVIEW, LINKS, CHARTS, HISTORY, STATISTICS, SETTINGS }

    private static final String[] RANGE_LABEL = {"Live", "1m", "5m", "15m", "1h", "6h", "24h", "7d", "30d", "All"};
    private static final long[] RANGE_MS = {120_000L, 60_000L, 300_000L, 900_000L, 3_600_000L, 21_600_000L, 86_400_000L,
            7 * 86_400_000L, 30 * 86_400_000L, 400L * 86_400_000L};
    private static final long MIN_SPAN = 20_000L, MAX_SPAN = 400L * 86_400_000L;

    private Tab tab = Tab.CHARTS;
    private int range = 4;
    private ChartWidget.Type type = ChartWidget.Type.LINE;
    private Metric metric = Metric.NET;
    private Unit unit = Unit.PER_MINUTE;
    private String selected = "";
    private final Set<String> hidden = new HashSet<>();
    private long vFrom, vTo;
    private boolean follow = true;
    private long lastRequest;
    private boolean pointsKnown;
    private double scroll;
    private final ChartWidget chart = new ChartWidget();
    private final List<Object[]> legendHits = new ArrayList<>(); // {x1,y1,x2,y2,itemId}
    private final List<Object[]> pointHits = new ArrayList<>();

    public AnalyticsScreen() {
        super(Component.literal("Factory Ledger Analytics"));
        setRange(range);
    }

    private void setRange(int i) {
        range = i;
        follow = true;
        long now = System.currentTimeMillis();
        vTo = now;
        vFrom = now - RANGE_MS[i];
        requestSoon();
    }

    /** Tabs with a second control row (unit / chart type) push the content down. */
    private int shift() { return tab == Tab.OVERVIEW || tab == Tab.CHARTS || tab == Tab.STATISTICS ? 16 : 0; }

    private void requestSoon() { lastRequest = 0; }

    private void sendRequest() {
        lastRequest = System.currentTimeMillis();
        PacketDistributor.sendToServer(new HistoryRequestPayload(selected, vFrom, vTo, 400, !pointsKnown || tab == Tab.LINKS));
    }

    @Override
    protected void init() {
        clearWidgets();
        int x = 12;
        for (Tab t : Tab.values()) {
            String label = t.name().charAt(0) + t.name().substring(1).toLowerCase();
            Button b = Button.builder(Component.literal(label), btn -> { tab = t; scroll = 0; rebuildWidgets(); })
                    .bounds(x, 8, 62, 16).build();
            b.active = t != tab;
            addRenderableWidget(b);
            x += 64;
        }
        int rx = 12;
        for (int i = 0; i < RANGE_LABEL.length; i++) {
            final int idx = i;
            Button b = Button.builder(Component.literal(RANGE_LABEL[i]), btn -> { setRange(idx); rebuildWidgets(); })
                    .bounds(rx, 28, 34, 14).build();
            b.active = idx != range || !follow;
            addRenderableWidget(b);
            rx += 35;
        }
        if (tab == Tab.OVERVIEW || tab == Tab.CHARTS || tab == Tab.STATISTICS) {
            String[] names = {"smallest", "middle", "largest"};
            addRenderableWidget(Button.builder(Component.literal("Unit: " + names[displayTier]), btn -> {
                displayTier = (displayTier + 1) % 3;
                rebuildWidgets();
            }).bounds(12, 44, 90, 14).build());
        }
        if (tab == Tab.CHARTS) {
            int cx = 106;
            addRenderableWidget(Button.builder(Component.literal(type.name()), btn -> {
                type = ChartWidget.Type.values()[(type.ordinal() + 1) % ChartWidget.Type.values().length];
                rebuildWidgets();
            }).bounds(cx, 44, 70, 14).build());
            addRenderableWidget(Button.builder(Component.literal(metric.name()), btn -> {
                metric = Metric.values()[(metric.ordinal() + 1) % Metric.values().length];
                rebuildWidgets();
            }).bounds(cx + 72, 44, 36, 14).build());
        }
        if (tab == Tab.SETTINGS) {
            addRenderableWidget(Button.builder(Component.literal("Unit: items" + unit.suffix), btn -> {
                unit = Unit.values()[(unit.ordinal() + 1) % Unit.values().length];
                rebuildWidgets();
            }).bounds(12, 60, 150, 16).build());
            String mode = ClientHooks.latest == null ? "?" : ClientHooks.latest.compactingMode();
            addRenderableWidget(Button.builder(Component.literal("Compacting mode: " + mode), btn -> {
                String[] modes = {"OFF", "DEDUPLICATE", "NORMALIZED"};
                String cur = ClientHooks.latest == null ? "NORMALIZED" : ClientHooks.latest.compactingMode();
                int next = 0;
                for (int i = 0; i < modes.length; i++) if (modes[i].equals(cur)) next = (i + 1) % modes.length;
                PacketDistributor.sendToServer(new SetModePayload(modes[next]));
                requestSoon();
            }).bounds(12, 82, 200, 16).build());
        }
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        if (follow) { long span = vTo - vFrom; vTo = now; vFrom = now - span; }
        if (now - lastRequest > AnalyticsConfig.CHART_UPDATE_SECONDS.get() * 1000L) sendRequest();
        HistoryResponsePayload d = ClientHooks.latest;
        if (d != null && d.hasPoints()) {
            pointsKnown = true;
            if (selected.isEmpty() && !d.selected().isEmpty()) selected = d.selected();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xE0101018);
    }

    private int displayTier; // 0 = smallest unit (nuggets), 1 = next (ingots), 2 = largest (blocks)

    private dev.flanalytics.network.TierDto tier(HistoryResponsePayload d, int i) {
        if (i >= d.ladders().size() || d.ladders().get(i).isEmpty()) return null;
        var l = d.ladders().get(i);
        return l.get(Math.min(displayTier, l.size() - 1));
    }

    private double div(HistoryResponsePayload d, int i) {
        var t = tier(d, i);
        return t == null ? 1.0 : t.perItem();
    }

    private String label(HistoryResponsePayload d, int i) {
        var t = tier(d, i);
        return name(t == null ? d.items().get(i) : t.itemId());
    }

    private double[] divs(HistoryResponsePayload d) {
        double[] a = new double[d.items().size()];
        for (int i = 0; i < a.length; i++) a[i] = div(d, i);
        return a;
    }

    private String name(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return id;
        return BuiltInRegistries.ITEM.get(rl).getDescription().getString();
    }

    private void icon(GuiGraphics g, String id, int x, int y) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(rl)), x, y);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        HistoryResponsePayload d = ClientHooks.latest;
        legendHits.clear();
        pointHits.clear();
        if (d == null) { g.drawString(font, "Waiting for server...", 14, 56, 0xFFAAAAAA); return; }
        String head = (selected.isEmpty() ? "no point" : selected) + (d.live() ? "  [live samples]" : "  [stored history]")
                + (follow ? "" : "  [paused - pan/zoom]");
        g.drawString(font, head, 14, 46 + shift(), 0xFFCCCCCC);
        switch (tab) {
            case CHARTS -> renderCharts(g, d, mx, my);
            case OVERVIEW -> renderOverview(g, d);
            case LINKS -> renderLinks(g, d);
            case HISTORY -> renderHistory(g, d);
            case STATISTICS -> renderStats(g, d);
            case SETTINGS -> renderSettings(g, d);
        }
    }

    private void renderCharts(GuiGraphics g, HistoryResponsePayload d, int mx, int my) {
        int legendH = 14 * (1 + (d.items().size() - 1) / Math.max(1, (width - 24) / 150));
        chart.x = 12; chart.y = 68 + shift(); chart.w = width - 24; chart.h = Math.max(60, height - chart.y - legendH - 10);
        chart.render(g, font, d, type, metric, unit, hidden, vFrom, vTo, mx, my, divs(d), i -> label(d, i));
        int lx = 12, ly = chart.y + chart.h + 6;
        for (int i = 0; i < d.items().size(); i++) {
            String id = d.items().get(i);
            String label = label(d, i);
            int wLabel = Math.min(140, font.width(label) + 24);
            if (lx + wLabel > width - 12) { lx = 12; ly += 14; }
            boolean off = hidden.contains(id);
            int col = ChartWidget.color(i);
            g.fill(lx, ly + 2, lx + 8, ly + 10, off ? 0xFF444444 : col);
            g.drawString(font, font.plainSubstrByWidth(label, 126), lx + 11, ly + 2, off ? 0xFF777777 : 0xFFEEEEEE);
            legendHits.add(new Object[]{lx, ly, lx + wLabel, ly + 12, id});
            lx += wLabel + 6;
        }
        g.drawString(font, "Scroll = zoom, drag = pan, click legend = show/hide", 12, height - 11, 0xFF888888);
    }

    private void renderOverview(GuiGraphics g, HistoryResponsePayload d) {
        int y = 66 + shift();
        String[] cols = {"Item", "Stock", "In/min", "Out/min", "Net/min", "Net/h", "Net/day", "Net total"};
        int[] cx = {32, 170, 230, 290, 350, 420, 490, 560};
        for (int i = 0; i < cols.length; i++) g.drawString(font, cols[i], cx[i], y, 0xFFFFD27F);
        y += 14;
        for (int i = 0; i < d.items().size(); i++) {
            int row = y + (int) (i * 14 - scroll);
            if (row < 80 || row > height - 14) continue;
            String id = d.items().get(i);
            icon(g, tier(d, i) == null ? id : tier(d, i).itemId(), 12, row - 4);
            double dv = div(d, i);
            double stock = lastStock(d, i) / dv;
            ChartModel.Stats in = ChartModel.stats(d.buckets(), i, Metric.IN, Unit.PER_MINUTE);
            ChartModel.Stats out = ChartModel.stats(d.buckets(), i, Metric.OUT, Unit.PER_MINUTE);
            ChartModel.Stats net = ChartModel.stats(d.buckets(), i, Metric.NET, Unit.PER_MINUTE);
            String[] vals = {font.plainSubstrByWidth(label(d, i), 130), ChartModel.formatRate(stock), ChartModel.formatRate(in.avg() / dv),
                    ChartModel.formatRate(out.avg() / dv), ChartModel.formatRate(net.avg() / dv), ChartModel.formatRate(net.avg() * 60 / dv),
                    ChartModel.formatRate(net.avg() * 1440 / dv), ChartModel.formatRate((net.totalIn() - net.totalOut()) / dv)};
            for (int k = 0; k < vals.length; k++) g.drawString(font, vals[k], cx[k], row, 0xFFEEEEEE);
        }
        g.drawString(font, "Values are changes of stored amounts (STOCK_DELTA), averaged over the visible range; gaps excluded.", 12, height - 12, 0xFF888888);
    }

    private static long lastStock(HistoryResponsePayload d, int itemIdx) {
        for (int bi = d.buckets().size() - 1; bi >= 0; bi--) {
            ChartBucket b = d.buckets().get(bi);
            for (int k = 0; k < b.item().length; k++) if (b.item()[k] == itemIdx) return b.stock()[k];
        }
        return 0;
    }

    private void renderLinks(GuiGraphics g, HistoryResponsePayload d) {
        int y = 66 + shift();
        g.drawString(font, "Measurement points (from Factory Ledger links) - click to select", 12, y, 0xFFFFD27F);
        y += 14;
        long now = System.currentTimeMillis();
        int i = 0;
        for (PointMeta m : d.points()) {
            int row = y + (int) (i++ * 14 - scroll);
            if (row < 80 || row > height - 14) continue;
            boolean sel = m.id().equals(selected);
            long age = (now - m.lastSampleMillis()) / 1000;
            String s = (sel ? "> " : "  ") + m.dimension() + " " + m.x() + ", " + m.y() + ", " + m.z() + "   mode " + m.mode()
                    + "   last sample " + (age > 120 ? age / 60 + " min" : age + " s") + " ago" + (age > 120 ? "  (not measured now)" : "");
            g.drawString(font, s, 12, row, sel ? 0xFFFFFFFF : 0xFFBBBBBB);
            pointHits.add(new Object[]{12, row - 2, width - 12, row + 11, m.id()});
        }
        if (d.points().isEmpty()) g.drawString(font, "No measurement points yet. Link an inventory in a Factory Ledger and wait a few seconds.", 12, y, 0xFFAAAAAA);
    }

    private void renderHistory(GuiGraphics g, HistoryResponsePayload d) {
        int y = 66 + shift();
        g.drawString(font, "Time", 12, y, 0xFFFFD27F);
        g.drawString(font, "Covered", 90, y, 0xFFFFD27F);
        g.drawString(font, "Gap", 150, y, 0xFFFFD27F);
        for (int k = 0; k < Math.min(3, d.items().size()); k++) g.drawString(font, font.plainSubstrByWidth(name(d.items().get(k)), 120), 200 + k * 130, y, ChartWidget.color(k));
        y += 14;
        List<ChartBucket> bs = d.buckets();
        for (int i = 0; i < bs.size(); i++) {
            ChartBucket b = bs.get(bs.size() - 1 - i);
            int row = y + (int) (i * 12 - scroll);
            if (row < 80 || row > height - 14) continue;
            g.drawString(font, ChartWidget.time(b.start(), vTo - vFrom), 12, row, 0xFFEEEEEE);
            g.drawString(font, b.coveredMs() / 1000 + "s", 90, row, 0xFFEEEEEE);
            g.drawString(font, b.gapMs() > 0 || b.coveredMs() == 0 ? "gap " + b.gapMs() / 1000 + "s" : "-", 150, row, b.gapMs() > 0 || b.coveredMs() == 0 ? 0xFFFF7777 : 0xFF777777);
            for (int k = 0; k < Math.min(3, d.items().size()); k++) {
                String v = "";
                for (int s = 0; s < b.item().length; s++) if (b.item()[s] == k) v = "+" + b.in()[s] + " / -" + b.out()[s];
                g.drawString(font, v, 200 + k * 130, row, 0xFFEEEEEE);
            }
        }
    }

    private void renderStats(GuiGraphics g, HistoryResponsePayload d) {
        int y = 66 + shift();
        String[] cols = {"Item", "Min" + unit.suffix, "Avg" + unit.suffix, "Max" + unit.suffix, "Total in", "Total out", "Covered", "Gaps"};
        int[] cx = {32, 170, 235, 300, 365, 430, 495, 560};
        for (int i = 0; i < cols.length; i++) g.drawString(font, cols[i], cx[i], y, 0xFFFFD27F);
        y += 14;
        for (int i = 0; i < d.items().size(); i++) {
            int row = y + (int) (i * 14 - scroll);
            if (row < 80 || row > height - 14) continue;
            ChartModel.Stats s = ChartModel.stats(d.buckets(), i, metric, unit);
            double dv = div(d, i);
            icon(g, tier(d, i) == null ? d.items().get(i) : tier(d, i).itemId(), 12, row - 4);
            String[] v = {font.plainSubstrByWidth(label(d, i), 130), ChartModel.formatRate(s.min() / dv), ChartModel.formatRate(s.avg() / dv),
                    ChartModel.formatRate(s.max() / dv), ChartModel.formatRate(s.totalIn() / dv), ChartModel.formatRate(s.totalOut() / dv),
                    s.coveredMs() / 1000 + "s", s.gapMs() / 1000 + "s"};
            for (int k = 0; k < v.length; k++) g.drawString(font, v[k], cx[k], row, 0xFFEEEEEE);
        }
        g.drawString(font, "Metric: " + metric + " (change on the Charts tab)", 12, height - 12, 0xFF888888);
    }

    private void renderSettings(GuiGraphics g, HistoryResponsePayload d) {
        g.drawString(font, "Display", 12, 50, 0xFFFFD27F);
        int y = 108;
        for (String line : new String[]{
                "Compacting mode (server, operators only):",
                "  OFF = count like Factory Ledger (can double count drawer tiers)",
                "  DEDUPLICATE = count each drawer chain once",
                "  NORMALIZED = convert to the smallest item using the real compacting rules",
                "Changing the mode starts a new baseline; old and new values are not mixed.",
                "Data = stock changes, not gross production. Measurement gaps are marked red."}) {
            g.drawString(font, line, 12, y, 0xFFBBBBBB);
            y += 12;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        for (Object[] h : legendHits) {
            if (mx >= (int) h[0] && mx <= (int) h[2] && my >= (int) h[1] && my <= (int) h[3]) {
                String id = (String) h[4];
                if (!hidden.remove(id)) hidden.add(id);
                return true;
            }
        }
        for (Object[] h : pointHits) {
            if (mx >= (int) h[0] && mx <= (int) h[2] && my >= (int) h[1] && my <= (int) h[3]) {
                selected = (String) h[4];
                hidden.clear();
                requestSoon();
                return true;
            }
        }
        return false;
    }

    private boolean inChart(double mx, double my) {
        return tab == Tab.CHARTS && mx >= chart.x && mx <= chart.x + chart.w && my >= chart.y && my <= chart.y + chart.h;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (inChart(mx, my)) {
            double anchor = Math.max(0, Math.min(1, (mx - (chart.x + 42)) / (chart.w - 48)));
            long[] r = ChartModel.zoom(vFrom, vTo, anchor, sy > 0 ? 0.8 : 1.25, MIN_SPAN, MAX_SPAN, System.currentTimeMillis());
            vFrom = r[0]; vTo = r[1];
            follow = false;
            requestSoon();
            return true;
        }
        scroll = Math.max(0, scroll - sy * 28);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && inChart(mx, my)) {
            long span = vTo - vFrom;
            long delta = -Math.round(dx / (chart.w - 48) * span);
            long[] r = ChartModel.pan(vFrom, vTo, delta, System.currentTimeMillis());
            vFrom = r[0]; vTo = r[1];
            follow = false;
            requestSoon();
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
