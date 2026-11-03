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
    public static final ModConfigSpec.IntValue GUI_PERMISSION_LEVEL;
    public static final ModConfigSpec.IntValue MODE_PERMISSION_LEVEL;
    public static final ModConfigSpec.BooleanValue API_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> API_BIND;
    public static final ModConfigSpec.IntValue API_PORT;
    public static final ModConfigSpec.ConfigValue<String> API_TOKEN;
    public static final ModConfigSpec.ConfigValue<String> API_ALLOWED_ORIGIN;
    public static final ModConfigSpec.IntValue API_RATE_LIMIT_PER_MINUTE;
    public static final ModConfigSpec.BooleanValue PUSH_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> PUSH_URL;
    public static final ModConfigSpec.ConfigValue<String> PUSH_SECRET;
    public static final ModConfigSpec.IntValue PUSH_INTERVAL_SECONDS;

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
        b.push("permissions");
        GUI_PERMISSION_LEVEL = b.comment("Minimum permission level (0 = everyone, 2 = operators) to read analytics data.").defineInRange("viewLevel", 0, 0, 4);
        MODE_PERMISSION_LEVEL = b.comment("Minimum permission level to change the compacting mode.").defineInRange("modeLevel", 2, 0, 4);
        b.pop();
        b.comment("Read-only HTTP API (GET). Needs a free port; many hosts only allow the game port - then use push.").push("api");
        API_ENABLED = b.define("enabled", false);
        API_BIND = b.comment("Use 127.0.0.1 behind a reverse proxy, 0.0.0.0 to listen publicly.").define("bind", "127.0.0.1");
        API_PORT = b.defineInRange("port", 8765, 1024, 65535);
        API_TOKEN = b.comment("Required bearer token, at least 16 random characters. Keep it secret.").define("token", "");
        API_ALLOWED_ORIGIN = b.comment("CORS origin of your website, e.g. https://example.com. Empty = no CORS.").define("allowedOrigin", "");
        API_RATE_LIMIT_PER_MINUTE = b.defineInRange("rateLimitPerMinute", 120, 1, 6000);
        b.pop();
        b.comment("Push the snapshot to your website (HTTPS POST, HMAC-signed).").push("push");
        PUSH_ENABLED = b.define("enabled", false);
        PUSH_URL = b.define("url", "");
        PUSH_SECRET = b.comment("Shared secret for the HMAC signature, at least 16 random characters.").define("secret", "");
        PUSH_INTERVAL_SECONDS = b.defineInRange("intervalSeconds", 30, 5, 3600);
        b.pop();
        SPEC = b.build();
    }

    private AnalyticsConfig() {}
}
