package com.evolveum.polygon.connector.googleapps.integration;

import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.directory.model.User;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

/** Small, stateful Directory users API fixture, not a complete Google API emulator. */
final class GoogleDirectoryMockServer implements AutoCloseable {
    private static final String USERS = "/admin/directory/v1/users";
    private final GsonFactory json = GsonFactory.getDefaultInstance();
    private final HttpServer server;
    private final ExecutorService executor;
    private final Map<String, User> users = new LinkedHashMap<>();
    private final List<String> requests = new ArrayList<>();
    private int sequence;
    private int revision;

    GoogleDirectoryMockServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "google-directory-mock");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    String rootUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    private synchronized void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            // Google's NetHttpTransport tunnels PATCH over POST.
            String override = exchange.getRequestHeaders().getFirst("X-HTTP-Method-Override");
            if ("POST".equals(method) && "PATCH".equals(override)) {
                method = "PATCH";
            }
            String path = exchange.getRequestURI().getPath();
            requests.add(method + " " + path);
            if ("POST".equals(method) && USERS.equals(path)) {
                create(exchange);
                return;
            }
            if (!path.startsWith(USERS + "/") || path.substring(USERS.length() + 1).contains("/")) {
                error(exchange, 400, "badRequest", "Endpoint not implemented by the mock");
                return;
            }
            String key = path.substring(USERS.length() + 1);
            User user = users.get(key);
            if (user == null) {
                user = users.values().stream().filter(item -> key.equalsIgnoreCase(item.getPrimaryEmail()))
                        .findFirst().orElse(null);
            }
            if (user == null) {
                error(exchange, 404, "notFound", "Resource not found");
                return;
            }
            switch (method) {
                case "GET":
                    respond(exchange, 200, user);
                    break;
                case "PATCH":
                    User patch = readBody(exchange);
                    if (patch.getName() == null || patch.getName().getGivenName() == null) {
                        error(exchange, 400, "invalid", "This mock supports patching name.givenName");
                        return;
                    }
                    user.getName().setGivenName(patch.getName().getGivenName());
                    if (patch.getName().getFamilyName() != null) {
                        user.getName().setFamilyName(patch.getName().getFamilyName());
                    }
                    user.setEtag(nextEtag());
                    respond(exchange, 200, user);
                    break;
                case "DELETE":
                    users.remove(user.getId());
                    exchange.sendResponseHeaders(204, -1);
                    break;
                default:
                    error(exchange, 405, "badRequest", "Method not implemented by the mock");
            }
        } catch (IllegalArgumentException malformedJson) {
            error(exchange, 400, "invalid", "Invalid JSON request");
        } finally {
            exchange.close();
        }
    }

    private void create(HttpExchange exchange) throws IOException {
        User user = readBody(exchange);
        if (user.getPrimaryEmail() == null || !user.getPrimaryEmail().endsWith("@mock.example.test")
                || user.getName() == null || user.getName().getGivenName() == null
                || user.getName().getFamilyName() == null || user.getPassword() == null) {
            error(exchange, 400, "invalid", "Missing user fields or wrong mock domain");
            return;
        }
        if (users.values().stream().anyMatch(item -> item.getPrimaryEmail().equalsIgnoreCase(user.getPrimaryEmail()))) {
            error(exchange, 409, "duplicate", "Entity already exists");
            return;
        }
        user.setId("mock-user-" + ++sequence);
        user.setEtag(nextEtag());
        user.setKind("admin#directory#user");
        user.setPassword(null); // Password is write-only: do not retain or return it.
        users.put(user.getId(), user);
        respond(exchange, 200, user);
    }

    private User readBody(HttpExchange exchange) throws IOException {
        InputStream body = exchange.getRequestBody();
        if ("gzip".equalsIgnoreCase(exchange.getRequestHeaders().getFirst("Content-Encoding"))) {
            body = new GZIPInputStream(body);
        }
        try (InputStream input = body) {
            return json.fromInputStream(input, StandardCharsets.UTF_8, User.class);
        }
    }

    private String nextEtag() {
        return "\"mock-revision-" + ++revision + "\"";
    }

    private void error(HttpExchange exchange, int code, String reason, String message) throws IOException {
        respond(exchange, code, Map.of("error", Map.of("code", code, "message", message,
                "errors", List.of(Map.of("domain", "global", "reason", reason, "message", message)))));
    }

    private void respond(HttpExchange exchange, int code, Object response) throws IOException {
        byte[] bytes = json.toString(response).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    synchronized List<String> requests() {
        return List.copyOf(requests);
    }

    synchronized int userCount() {
        return users.size();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
