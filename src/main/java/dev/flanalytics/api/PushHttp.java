package dev.flanalytics.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** The HTTP part of the website push, free of config and Minecraft so it can be tested against a local server. */
public final class PushHttp {
    private PushHttp() {}

    /** Only https (or http to localhost) is accepted; anything else returns null. */
    public static URI parseTarget(String url) {
        try {
            URI u = URI.create(url.trim());
            String host = u.getHost();
            boolean local = "localhost".equals(host) || "127.0.0.1".equals(host);
            if ("https".equals(u.getScheme()) || local && "http".equals(u.getScheme())) return u;
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    /** One signed POST; returns the HTTP status. Signature = hex(HMAC-SHA256(secret, timestamp + "." + body)). */
    public static int post(HttpClient client, URI uri, String secret, String body, long nowMillis) throws Exception {
        String ts = Long.toString(nowMillis);
        HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-FLA-Timestamp", ts)
                .header("X-FLA-Signature", Hmac.sign(secret, ts + "." + body))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        return client.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
