package dev.flanalytics.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AnalyticsConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue MONITORING_ENABLED;
    public static final ModConfigSpec.IntValue SAMPLE_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue SAVE_INTERVAL_SECONDS;
    public static final ModConfigSpec.IntValue RETENTION_DAYS;
    public static final ModConfigSpec.IntValue MAX_MEASUREMENT_POINTS;
    public static final ModConfigSpec.IntValue MAX_HISTORY_MB;
    public static final ModConfigSpec.IntValue CHART_UPDATE_SECONDS;
    public static final ModConfigSpec.BooleanValue DEBUG;
    public static final ModConfigSpec.BooleanValue EXPORT_ENABLED;
    public static final ModConfigSpec.EnumValue<CompactingMode> COMPACTING_MODE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        MONITORING_ENABLED = b.comment("Master switch for all measurement.").define("monitoringEnabled", true);
        SAMPLE_INTERVAL_TICKS = b.comment("Ticks between measurements (20 = 1 s).").defineInRange("sampleIntervalTicks", 20, 2, 1200);
        SAVE_INTERVAL_SECONDS = b.defineInRange("saveIntervalSeconds", 60, 10, 3600);
        RETENTION_DAYS = b.comment("Raw data is aggregated; this is the maximum age of any stored data.").defineInRange("retentionDays", 90, 1, 3650);
        MAX_MEASUREMENT_POINTS = b.defineInRange("maxMeasurementPoints", 256, 1, 4096);
        MAX_HISTORY_MB = b.defineInRange("maxHistorySizeMb", 256, 8, 4096);
        CHART_UPDATE_SECONDS = b.defineInRange("chartUpdateSeconds", 2, 1, 60);
        DEBUG = b.define("debug", false);
        EXPORT_ENABLED = b.define("exportEnabled", true);
        b.push("compacting");
        COMPACTING_MODE = b.comment("OFF = like Factory Ledger (duplicates), DEDUPLICATE = count chain once,",
                "NORMALIZED = convert chain to one base unit using the real compacting rules.")
                .defineEnum("mode", CompactingMode.NORMALIZED);
        b.pop();
        SPEC = b.build();
    }

    private AnalyticsConfig() {}
}
