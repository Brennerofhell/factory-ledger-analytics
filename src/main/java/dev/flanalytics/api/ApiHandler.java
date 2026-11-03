package dev.flanalytics.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Request handling of the read-only API, separated from the config and from Minecraft so it can be tested with a
 * plain local HttpServer. GET only; bearer token compared in constant time; per-minute rate limit; optional CORS.
 */
public final class ApiHandler implements HttpHandler {
    private final Supplier<String> token;
    private final IntSupplier rateLimitPerMinute;
    private final Supplier<String> allowedOrigin;
    private final Supplier<String> snapshotJson;
    private final AtomicLong windowStart = new AtomicLong();
    private final AtomicLong windowCount = new AtomicLong();

    public ApiHandler(Supplier<String> token, IntSupplier rateLimitPerMinute, Supplier<String> allowedOrigin,
                      Supplier<String> snapshotJson) {
        this.token = token;
        this.rateLimitPerMinute = rateLimitPerMinute;
        this.allowedOrigin = allowedOrigin;
        this.snapshotJson = snapshotJson;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            addCors(ex);
            String method = ex.getRequestMethod();
            if ("OPTIONS".equals(method)) { send(ex, 204, ""); return; }
            if (!"GET".equals(method)) { send(ex, 405, error("method not allowed")); return; }
            if (!authorized(ex)) { send(ex, 401, error("unauthorized")); return; }
            if (!allowRequest()) { send(ex, 429, error("rate limit")); return; }
            String path = ex.getRequestURI().getPath();
            if (path.endsWith("/health")) { send(ex, 200, "{\"status\":\"ok\",\"version\":" + ApiSnapshot.VERSION + "}"); return; }
            send(ex, 200, snapshotJson.get());
        } catch (RuntimeException e) {
            send(ex, 500, error("internal error"));
        } finally {
            ex.close();
        }
    }

    private boolean authorized(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("Authorization");
        if (h == null || !h.startsWith("Bearer ")) return false;
        String want = token.get();
        if (want == null || want.isEmpty()) return false;
        byte[] given = h.substring(7).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(given, want.getBytes(StandardCharsets.UTF_8)); // constant time
    }

    private boolean allowRequest() {
        long now = System.currentTimeMillis();
        long start = windowStart.get();
        if (now - start > 60_000 && windowStart.compareAndSet(start, now)) windowCount.set(0);
        return windowCount.incrementAndGet() <= rateLimitPerMinute.getAsInt();
    }

    private void addCors(HttpExchange ex) {
        String origin = allowedOrigin.get();
        if (origin == null || origin.isEmpty()) return;
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Authorization");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
    }

    private static String error(String msg) {
        return "{\"error\":\"" + msg.replace("\"", "'") + "\"}";
    }

    private static void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, b.length == 0 ? -1 : b.length);
        if (b.length > 0) try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }
}
