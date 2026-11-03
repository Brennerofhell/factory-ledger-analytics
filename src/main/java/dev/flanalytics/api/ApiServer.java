package dev.flanalytics.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpServer;
import dev.flanalytics.FactoryLedgerAnalytics;
import dev.flanalytics.config.AnalyticsConfig;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Optional read-only HTTP API (GET only). Disabled by default; refuses to start without a token.
 * Runs on its own daemon threads and only reads thread-safe copies, never the server thread's state.
 */
public final class ApiServer {
    private static final Gson GSON = new GsonBuilder().create();
    private static final long WINDOW_MS = 5 * 60_000L;

    private HttpServer server;
    private ExecutorService pool;

    public synchronized void start() {
        if (server != null || !AnalyticsConfig.API_ENABLED.get()) return;
        String token = AnalyticsConfig.API_TOKEN.get();
        if (token.length() < 16) {
            FactoryLedgerAnalytics.LOGGER.error("API disabled: set api.token to a random value of at least 16 characters.");
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(AnalyticsConfig.API_BIND.get(), AnalyticsConfig.API_PORT.get()), 16);
            pool = Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "fla-api");
                t.setDaemon(true);
                return t;
            });
            server.setExecutor(pool);
            ApiHandler handler = new ApiHandler(AnalyticsConfig.API_TOKEN::get, AnalyticsConfig.API_RATE_LIMIT_PER_MINUTE::get,
                    AnalyticsConfig.API_ALLOWED_ORIGIN::get,
                    () -> GSON.toJson(ApiSnapshot.build(FactoryLedgerAnalytics.MONITORING.recentSamples(), System.currentTimeMillis(), WINDOW_MS)));
            server.createContext("/api/v1/snapshot", handler);
            server.createContext("/api/v1/health", handler);
            server.start();
            FactoryLedgerAnalytics.LOGGER.info("Analytics API listening on {}:{}", AnalyticsConfig.API_BIND.get(), AnalyticsConfig.API_PORT.get());
        } catch (IOException | RuntimeException e) {
            FactoryLedgerAnalytics.LOGGER.error("Analytics API could not start: {}", e.toString());
            stop();
        }
    }

    public synchronized void stop() {
        if (server != null) server.stop(0);
        if (pool != null) pool.shutdownNow();
        server = null;
        pool = null;
    }
}
