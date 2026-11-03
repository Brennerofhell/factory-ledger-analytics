package dev.flanalytics.monitoring;

import dev.flanalytics.FactoryLedgerAnalytics;
import dev.flanalytics.config.AnalyticsConfig;
import dev.flanalytics.integration.DrawersCompat;
import dev.flanalytics.integration.LedgerRegistry;
import dev.flanalytics.integration.McRuleSource;
import dk.draaby.factoryledger.block.LedgerBlockEntity;
import dk.draaby.factoryledger.block.LinkKind;
import dk.draaby.factoryledger.block.LinkStatus;
import dk.draaby.factoryledger.block.LinkView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Server-side measurement. Reuses the Factory Ledger link list (no second set-up needed) and samples the
 * linked inventories on a configurable interval. Works without players online as long as chunks tick.
 */
public final class MonitoringService {
    private static final int RECENT_LIMIT = 2048;

    private static final class Point {
        final DeltaTracker tracker = new DeltaTracker();
        long lastSeenSample = -1;
    }

    private final LedgerRegistry ledgers = new LedgerRegistry();
    private final Map<String, Point> points = new HashMap<>();
    private final Map<String, ChainNormalizer> normalizers = new HashMap<>();
    private final Deque<MeasurementSample> recent = new ArrayDeque<>();
    private final List<Consumer<MeasurementSample>> sinks = new ArrayList<>();
    private boolean drawersLoaded;
    private long tickCounter;
    private long sampleRound;

    public void register() {
        drawersLoaded = ModList.get().isLoaded("storagedrawers");
        NeoForge.EVENT_BUS.addListener(ledgers::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(ledgers::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(ledgers::onPlace);
        NeoForge.EVENT_BUS.addListener(this::onTick);
        NeoForge.EVENT_BUS.addListener(this::onStopping);
    }

    public void addSink(Consumer<MeasurementSample> sink) {
        sinks.add(sink);
    }

    /** Thread-safe copy; safe to call from the API / push threads. */
    public synchronized List<MeasurementSample> recentSamples() {
        return new ArrayList<>(recent);
    }

    /** Called when recipes / compacting rules may have changed. */
    public void invalidateRules() {
        normalizers.values().forEach(ChainNormalizer::invalidate);
    }

    private void onStopping(ServerStoppingEvent e) {
        points.values().forEach(p -> p.tracker.markGap());
        ledgers.clear();
    }

    private void onTick(ServerTickEvent.Post e) {
        if (!AnalyticsConfig.MONITORING_ENABLED.get()) return;
        int interval = AnalyticsConfig.SAMPLE_INTERVAL_TICKS.get();
        if (++tickCounter % interval != 0) return;
        sampleRound++;
        for (ServerLevel level : e.getServer().getAllLevels()) sampleLevel(level, interval);
        for (Point p : points.values()) {
            if (p.lastSeenSample != sampleRound) p.tracker.markGap(); // ledger/chunk not ticking: honest gap
        }
    }

    private void sampleLevel(ServerLevel level, int interval) {
        Map<BlockPos, BlockPos> links = new HashMap<>(); // inventory pos -> owning ledger pos
        for (BlockPos ledgerPos : ledgers.positions(level.dimension())) {
            if (!level.isLoaded(ledgerPos)) continue;
            BlockEntity be = level.getBlockEntity(ledgerPos);
            if (!(be instanceof LedgerBlockEntity ledger)) {
                ledgers.remove(level.dimension(), ledgerPos);
                continue;
            }
            for (LinkView v : ledger.getViews()) {
                if (v.kind() == LinkKind.INVENTORY && v.status() == LinkStatus.OK) {
                    links.putIfAbsent(v.pos().immutable(), ledgerPos); // several ledgers, one inventory: sample once
                }
            }
        }
        for (Map.Entry<BlockPos, BlockPos> e : links.entrySet()) sampleInventory(level, e.getKey(), e.getValue(), interval);
    }

    private void sampleInventory(ServerLevel level, BlockPos pos, BlockPos ledgerPos, int interval) {
        String dim = level.dimension().location().toString();
        String id = dim + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        Point point = points.get(id);
        if (point == null) {
            if (points.size() >= AnalyticsConfig.MAX_MEASUREMENT_POINTS.get()) return;
            point = new Point();
            points.put(id, point);
        }
        point.lastSeenSample = sampleRound;
        if (!level.isLoaded(pos)) { point.tracker.markGap(); return; }
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler == null) { point.tracker.markGap(); return; }

        BlockEntity be = level.getBlockEntity(pos);
        boolean compacting = drawersLoaded && be != null && DrawersCompat.isCompacting(be);
        List<StockSampler.SlotStack> slots = new ArrayList<>(handler.getSlots());
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (!s.isEmpty()) slots.add(new StockSampler.SlotStack(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount()));
        }
        var mode = AnalyticsConfig.COMPACTING_MODE.get();
        ChainNormalizer norm = normalizers.computeIfAbsent(dim,
                k -> new ChainNormalizer(drawersLoaded ? new McRuleSource(level) : item -> null));
        DeltaTracker.Result r = point.tracker.update(StockSampler.snapshot(slots, mode, compacting, norm));
        MeasurementSample sample = new MeasurementSample(id, dim, pos.getX(), pos.getY(), pos.getZ(),
                ledgerPos.getX(), ledgerPos.getY(), ledgerPos.getZ(), level.getGameTime(), System.currentTimeMillis(),
                interval, mode, compacting, r.gapBefore(), r.baseline(), r.stock(), r.deltas());
        publish(sample);
    }

    private synchronized void publish(MeasurementSample s) {
        recent.addLast(s);
        while (recent.size() > RECENT_LIMIT) recent.removeFirst();
        for (Consumer<MeasurementSample> sink : sinks) sink.accept(s);
        if (AnalyticsConfig.DEBUG.get()) FactoryLedgerAnalytics.LOGGER.info("sample {} {}", s.pointId(), s.deltas());
    }
}
