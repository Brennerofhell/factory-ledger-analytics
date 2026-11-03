package dev.flanalytics.api;

import com.google.gson.Gson;
import dev.flanalytics.FactoryLedgerAnalytics;
import dev.flanalytics.config.AnalyticsConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Optional push to the owner's website (works when the host cannot open an extra port).
 * Every request carries {@code X-FLA-Timestamp} and {@code X-FLA-Signature} = hex(HMAC-SHA256(secret, timestamp + "." + body)).
 * Runs on its own thread; failures back off and never touch the server thread.
 */
public final class WebhookPusher {
    private static final Gson GSON = new Gson();

    private ScheduledExecutorService exec;
    private HttpClient client;
    private int failures;

    public synchronized void start() {
        if (exec != null || !AnalyticsConfig.PUSH_ENABLED.get()) return;
        URI uri = target();
        String secret = AnalyticsConfig.PUSH_SECRET.get();
        if (uri == null || secret.length() < 16) {
            FactoryLedgerAnalytics.LOGGER.error("Push disabled: push.url must be https (or localhost) and push.secret >= 16 chars.");
            return;
        }
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "fla-push");
            t.setDaemon(true);
            return t;
        });
        schedule(AnalyticsConfig.PUSH_INTERVAL_SECONDS.get());
    }

    public synchronized void stop() {
        if (exec != null) exec.shutdownNow();
        exec = null;
    }

    private synchronized void schedule(long seconds) {
        if (exec != null && !exec.isShutdown()) exec.schedule(this::pushOnce, seconds, TimeUnit.SECONDS);
    }

    private void pushOnce() {
        long next = AnalyticsConfig.PUSH_INTERVAL_SECONDS.get();
        try {
            URI uri = target();
            if (uri == null) { schedule(next); return; }
            long now = System.currentTimeMillis();
            String body = GSON.toJson(ApiSnapshot.build(FactoryLedgerAnalytics.MONITORING.recentSamples(), now, 5 * 60_000L));
            String ts = Long.toString(now);
            HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("X-FLA-Timestamp", ts)
                    .header("X-FLA-Signature", Hmac.sign(AnalyticsConfig.PUSH_SECRET.get(), ts + "." + body))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            if (resp.statusCode() / 100 == 2) failures = 0;
            else throw new IllegalStateException("HTTP " + resp.statusCode());
        } catch (Exception e) {
            failures++;
            if (failures == 1 || failures % 20 == 0) FactoryLedgerAnalytics.LOGGER.warn("Push failed ({}x): {}", failures, e.toString());
            next = Math.max(next, Math.min(600, next * (1L << Math.min(failures, 5)))); // exponential backoff, max 10 min
        }
        schedule(next);
    }

    private static URI target() {
        try {
            URI u = URI.create(AnalyticsConfig.PUSH_URL.get().trim());
            String host = u.getHost();
            boolean local = "localhost".equals(host) || "127.0.0.1".equals(host);
            if ("https".equals(u.getScheme()) || local && "http".equals(u.getScheme())) return u;
        } catch (RuntimeException ignored) {
        }
        return null;
    }

}
