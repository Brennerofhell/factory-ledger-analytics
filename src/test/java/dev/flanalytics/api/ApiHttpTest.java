package dev.flanalytics.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Real local HTTP round trips for the pull API and the signed push (no Minecraft needed). */
class ApiHttpTest {
    private static final String TOKEN = java.util.UUID.randomUUID().toString(); // random per run
    private static final String SECRET = java.util.UUID.randomUUID().toString();
    private HttpServer server;
    private final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private URI startApi(int rateLimit, String origin) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 4);
        ApiHandler h = new ApiHandler(() -> TOKEN, () -> rateLimit, () -> origin, () -> "{\"points\":[]}");
        server.createContext("/api/v1/snapshot", h);
        server.createContext("/api/v1/health", h);
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private HttpResponse<String> get(URI base, String path, String auth) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(base.resolve(path));
        if (auth != null) b.header("Authorization", auth);
        return client.send(b.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void snapshotNeedsTheToken() throws Exception {
        URI base = startApi(100, "");
        assertEquals(401, get(base, "/api/v1/snapshot", null).statusCode());
        assertEquals(401, get(base, "/api/v1/snapshot", "Bearer wrong").statusCode());
        assertEquals(401, get(base, "/api/v1/snapshot", TOKEN).statusCode()); // missing "Bearer "
        HttpResponse<String> ok = get(base, "/api/v1/snapshot", "Bearer " + TOKEN);
        assertEquals(200, ok.statusCode());
        assertEquals("{\"points\":[]}", ok.body());
        assertTrue(ok.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        assertEquals("no-store", ok.headers().firstValue("Cache-Control").orElse(""));
    }

    @Test
    void healthAndMethods() throws Exception {
        URI base = startApi(100, "");
        HttpResponse<String> h = get(base, "/api/v1/health", "Bearer " + TOKEN);
        assertEquals(200, h.statusCode());
        assertTrue(h.body().contains("\"status\":\"ok\""));
        HttpRequest post = HttpRequest.newBuilder(base.resolve("/api/v1/snapshot"))
                .header("Authorization", "Bearer " + TOKEN).POST(HttpRequest.BodyPublishers.ofString("x")).build();
        assertEquals(405, client.send(post, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void rateLimitReturns429() throws Exception {
        URI base = startApi(3, "");
        for (int i = 0; i < 3; i++) assertEquals(200, get(base, "/api/v1/health", "Bearer " + TOKEN).statusCode());
        assertEquals(429, get(base, "/api/v1/health", "Bearer " + TOKEN).statusCode());
    }

    @Test
    void emptyTokenConfigNeverAuthorizes() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 4);
        server.createContext("/api/v1/health", new ApiHandler(() -> "", () -> 10, () -> "", () -> "{}"));
        server.start();
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        assertEquals(401, get(base, "/api/v1/health", "Bearer ").statusCode());
    }

    @Test
    void corsOnlyWhenConfigured() throws Exception {
        URI base = startApi(100, "https://example.org");
        HttpResponse<String> r = get(base, "/api/v1/health", "Bearer " + TOKEN);
        assertEquals("https://example.org", r.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
        HttpRequest opt = HttpRequest.newBuilder(base.resolve("/api/v1/snapshot"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
        assertEquals(204, client.send(opt, HttpResponse.BodyHandlers.ofString()).statusCode());
        server.stop(0);
        URI base2 = startApi(100, "");
        assertTrue(get(base2, "/api/v1/health", "Bearer " + TOKEN).headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    }

    @Test
    void pushIsSignedAndVerifiable() throws Exception {
        AtomicReference<String> sig = new AtomicReference<>(), ts = new AtomicReference<>(), body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 4);
        server.createContext("/hook", ex -> {
            sig.set(ex.getRequestHeaders().getFirst("X-FLA-Signature"));
            ts.set(ex.getRequestHeaders().getFirst("X-FLA-Timestamp"));
            body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            ex.sendResponseHeaders(204, -1);
            ex.close();
        });
        server.start();
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/hook");
        String payload = "{\"version\":1,\"points\":[{\"id\":\"ä\"}]}";
        int status = PushHttp.post(client, uri, SECRET, payload, 1_700_000_000_000L);
        assertEquals(204, status);
        assertEquals(payload, body.get()); // UTF-8 body arrives unchanged
        assertEquals("1700000000000", ts.get());
        // what the website does: recompute over timestamp + "." + raw body
        assertEquals(Hmac.sign(SECRET, ts.get() + "." + body.get()), sig.get());
        assertNotNull(sig.get());
        assertTrue(!Hmac.sign("another-" + SECRET, ts.get() + "." + body.get()).equals(sig.get()));
    }

    @Test
    void pushRejectsPlainHttpToRemoteHosts() {
        assertNull(PushHttp.parseTarget("http://example.org/hook"));
        assertNull(PushHttp.parseTarget("ftp://example.org/hook"));
        assertNull(PushHttp.parseTarget("not a url"));
        assertNotNull(PushHttp.parseTarget("https://example.org/hook"));
        assertNotNull(PushHttp.parseTarget("http://localhost:8080/hook"));
        assertNotNull(PushHttp.parseTarget("http://127.0.0.1/hook"));
    }
}
