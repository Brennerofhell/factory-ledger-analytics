package dev.flanalytics.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.flanalytics.FactoryLedgerAnalytics;
import dev.flanalytics.config.AnalyticsConfig;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Optional read-only HTTP API (GET only). Disabled by default; refuses to start without a token.
 * Runs on its own daemon threads and only reads thread-safe copies, never the server thread's state.
 */
public final class ApiServer {
    private static final Gson GSON = new GsonBuilder().create();
    private static final long WINDOW_MS = 5 * 60_000L;

    private HttpServer server;
    private ExecutorService pool;
    private final AtomicLong windowStart = new AtomicLong();
    private final AtomicLong windowCount = new AtomicLong();

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
            server.createContext("/api/v1/snapshot", this::handle);
            server.createContext("/api/v1/health", this::handle);
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

    private void handle(HttpExchange ex) throws IOException {
        try {
            addCors(ex);
            String method = ex.getRequestMethod();
            if ("OPTIONS".equals(method)) { send(ex, 204, ""); return; }
            if (!"GET".equals(method)) { send(ex, 405, error("method not allowed")); return; }
            if (!authorized(ex)) { send(ex, 401, error("unauthorized")); return; }
            if (!allowRequest()) { send(ex, 429, error("rate limit")); return; }
            String path = ex.getRequestURI().getPath();
            if (path.endsWith("/health")) { send(ex, 200, GSON.toJson(Map.of("status", "ok", "version", ApiSnapshot.VERSION))); return; }
            long now = System.currentTimeMillis();
            Map<String, Object> snap = ApiSnapshot.build(FactoryLedgerAnalytics.MONITORING.recentSamples(), now, WINDOW_MS);
            send(ex, 200, GSON.toJson(snap));
        } catch (RuntimeException e) {
            send(ex, 500, error("internal error"));
        } finally {
            ex.close();
        }
    }

    private boolean authorized(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("Authorization");
        if (h == null || !h.startsWith("Bearer ")) return false;
        byte[] given = h.substring(7).getBytes(StandardCharsets.UTF_8);
        byte[] want = AnalyticsConfig.API_TOKEN.get().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(given, want); // constant time
    }

    private boolean allowRequest() {
        long now = System.currentTimeMillis();
        long start = windowStart.get();
        if (now - start > 60_000 && windowStart.compareAndSet(start, now)) windowCount.set(0);
        return windowCount.incrementAndGet() <= AnalyticsConfig.API_RATE_LIMIT_PER_MINUTE.get();
    }

    private void addCors(HttpExchange ex) {
        String origin = AnalyticsConfig.API_ALLOWED_ORIGIN.get();
        if (origin.isEmpty()) return;
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Authorization");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
    }

    private static String error(String msg) {
        return GSON.toJson(Map.of("error", msg));
    }

    private static void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, b.length == 0 ? -1 : b.length);
        if (b.length > 0) try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }
}
