package dev.flanalytics;

import dev.flanalytics.config.AnalyticsConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(FactoryLedgerAnalytics.MOD_ID)
public class FactoryLedgerAnalytics {
    public static final String MOD_ID = "factoryledgeranalytics";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public FactoryLedgerAnalytics(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, AnalyticsConfig.SPEC);
    }
}
