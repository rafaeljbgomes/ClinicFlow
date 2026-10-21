package com.clinicflow.clinical.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal real HTTP server that stands in for a neighbouring service. Replies can be bound to one bearer
 * token so several callers can be simulated; unknown routes answer 404.
 */
public final class StubHttpServer implements AutoCloseable {
    private static final String ANY_TOKEN = "*";

    private final HttpServer server;
    private final Map<String, Reply> replies = new ConcurrentHashMap<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    private StubHttpServer(HttpServer server) {
        this.server = server;
        server.createContext("/", this::handle);
        server.start();
    }

    public static StubHttpServer start() {
        try {
            return new StubHttpServer(HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public String baseUrl() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }

    public void reply(String method, String path, int status) {
        reply(method, path, status, Map.of(), "");
    }

    public void reply(String method, String path, int status, Map<String, String> headers, String body) {
        replies.put(key(ANY_TOKEN, method, path), new Reply(status, headers, body));
    }

    public void replyTo(String bearerToken, String method, String path, int status, String jsonBody) {
        Map<String, String> headers = jsonBody.isEmpty() ? Map.of() : Map.of("Content-Type", "application/json");
        replies.put(key(bearerToken, method, path), new Reply(status, headers, jsonBody));
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    public void reset() {
        replies.clear();
        requests.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        requests.add(new Request(method, path, authorization, exchange.getRequestHeaders().getFirst("X-Correlation-Id")));
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : "";
        Reply reply = replies.getOrDefault(key(token, method, path),
                replies.getOrDefault(key(ANY_TOKEN, method, path), new Reply(404, Map.of(), "")));
        reply.headers().forEach((name, value) -> exchange.getResponseHeaders().set(name, value));
        byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
        boolean bodiless = body.length == 0 || "HEAD".equals(method);
        exchange.sendResponseHeaders(reply.status(), bodiless ? -1 : body.length);
        if (!bodiless) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }

    private static String key(String token, String method, String path) {
        return token + " " + method + " " + path;
    }

    public record Request(String method, String path, String authorization, String correlationId) {
    }

    private record Reply(int status, Map<String, String> headers, String body) {
    }
}
