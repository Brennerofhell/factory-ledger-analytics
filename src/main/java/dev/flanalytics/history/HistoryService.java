package dev.flanalytics.history;

import dev.flanalytics.FactoryLedgerAnalytics;
import dev.flanalytics.config.AnalyticsConfig;
import dev.flanalytics.monitoring.MeasurementSample;
import dev.flanalytics.storage.HistoryRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Owns the persistent history: loads at world start, saves on an interval and at shutdown, never on the server thread's I/O path. */
public final class HistoryService {
    private final Map<String, PointHistory> points = new ConcurrentHashMap<>();
    private volatile HistoryRepository repo;
    private long tickCounter;

    public void register() {
        NeoForge.EVENT_BUS.addListener(this::onAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onTick);
        NeoForge.EVENT_BUS.addListener(this::onStopping);
    }

    private void onAboutToStart(ServerAboutToStartEvent e) {
        points.clear();
        repo = new HistoryRepository(e.getServer().getWorldPath(LevelResource.ROOT).resolve("factoryledgeranalytics"),
                m -> FactoryLedgerAnalytics.LOGGER.warn("history: {}", m));
        try {
            points.putAll(repo.loadAll());
            FactoryLedgerAnalytics.LOGGER.info("Loaded history of {} measurement points", points.size());
        } catch (IOException ex) {
            FactoryLedgerAnalytics.LOGGER.error("Could not load history: {}", ex.toString());
        }
    }

    /** Sink for {@link dev.flanalytics.monitoring.MonitoringService}. */
    public void accept(MeasurementSample s) {
        if (repo == null) return;
        points.computeIfAbsent(s.pointId(), PointHistory::new).add(s);
    }

    private void onTick(ServerTickEvent.Post e) {
        HistoryRepository r = repo;
        if (r == null) return;
        long every = AnalyticsConfig.SAVE_INTERVAL_SECONDS.get() * 20L;
        if (++tickCounter % every != 0) return;
        long now = System.currentTimeMillis();
        boolean overBudget = r.totalBytes() > AnalyticsConfig.MAX_HISTORY_MB.get() * 1024L * 1024L;
        for (PointHistory p : points.values()) {
            p.compact(now, AnalyticsConfig.RETENTION_DAYS.get());
            if (overBudget) p.trimOldest();
            if (p.isDirty()) r.saveAsync(p);
        }
    }

    private void onStopping(ServerStoppingEvent e) {
        HistoryRepository r = repo;
        if (r == null) return;
        long now = System.currentTimeMillis();
        for (PointHistory p : points.values()) {
            p.compact(now, AnalyticsConfig.RETENTION_DAYS.get());
            if (p.isDirty()) r.saveAsync(p);
        }
        r.close(15);
        repo = null;
    }

    public List<String> pointIds() {
        return new ArrayList<>(points.keySet());
    }

    public PointHistory point(String id) {
        return points.get(id);
    }
}
