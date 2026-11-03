package dev.flanalytics;

import dev.flanalytics.api.ApiServer;
import dev.flanalytics.api.WebhookPusher;
import dev.flanalytics.config.AnalyticsConfig;
import dev.flanalytics.monitoring.MonitoringService;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(FactoryLedgerAnalytics.MOD_ID)
public class FactoryLedgerAnalytics {
    public static final String MOD_ID = "factoryledgeranalytics";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final MonitoringService MONITORING = new MonitoringService();
    public static final dev.flanalytics.history.HistoryService HISTORY = new dev.flanalytics.history.HistoryService();

    private final ApiServer api = new ApiServer();
    private final WebhookPusher pusher = new WebhookPusher();

    public FactoryLedgerAnalytics(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, AnalyticsConfig.SPEC);
        modBus.addListener(dev.flanalytics.network.AnalyticsNetwork::register);
        MONITORING.register();
        HISTORY.register();
        MONITORING.addSink(HISTORY::accept);
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> {
            api.start();
            pusher.start();
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
            api.stop();
            pusher.stop();
        });
    }
}
