package com.benjch.www.command;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.CmdLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.benjch.www.cli.Command;
import com.benjch.www.photosorter.AppConfigStore;
import com.benjch.www.photosorter.PhotoService;
import com.benjch.www.photosorter.PhotoService.FolderEntries;
import com.benjch.www.photosorter.PhotoService.KeepResult;
import com.benjch.www.photosorter.ThumbnailCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class ServePhotoSorterCommand implements Command {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServePhotoSorterCommand.class);

    @Option(name = "-port", usage = "Port to run local web app", required = false)
    private int port = 8080;

    @Option(name = "-keepDir", usage = "Default keep folder", required = false)
    private String keepDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void execute() {
        try {
            AppConfigStore configStore = new AppConfigStore();
            if (keepDir != null && !keepDir.isBlank()) {
                configStore.updateKeepDir(Path.of(keepDir).toAbsolutePath().normalize().toString());
            }

            PhotoService photoService = new PhotoService(new ThumbnailCache());
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);

            server.createContext("/api/folder/entries", wrap(exchange -> {
                requireMethod(exchange, "GET");
                String path = query(exchange.getRequestURI()).getOrDefault("path", "");
                FolderEntries entries = photoService.listFolderEntries(path);
                sendJson(exchange, 200, entries);
            }));

            server.createContext("/api/image", wrap(exchange -> {
                requireMethod(exchange, "GET");
                String path = query(exchange.getRequestURI()).get("path");
                byte[] bytes = photoService.loadImage(path);
                sendBinary(exchange, 200, PhotoService.contentTypeFor(path), bytes);
            }));

            server.createContext("/api/thumbnail", wrap(exchange -> {
                requireMethod(exchange, "GET");
                Map<String, String> query = query(exchange.getRequestURI());
                String path = query.get("path");
                int size = Integer.parseInt(query.getOrDefault("size", "280"));
                byte[] bytes = photoService.loadThumbnail(path, size);
                exchange.getResponseHeaders().set("Cache-Control", "public, max-age=300");
                sendBinary(exchange, 200, "image/jpeg", bytes);
            }));

            server.createContext("/api/delete", wrap(exchange -> {
                requireMethod(exchange, "POST");
                Map<String, String> body = bodyAsMap(exchange);
                photoService.deleteImage(body.get("path"));
                sendJson(exchange, 200, Map.of("status", "ok"));
            }));

            server.createContext("/api/keep", wrap(exchange -> {
                requireMethod(exchange, "POST");
                Map<String, String> body = bodyAsMap(exchange);
                String keepFolder = body.get("keepDir");
                if (keepFolder == null || keepFolder.isBlank()) {
                    keepFolder = configStore.readKeepDir();
                }
                if (keepFolder == null || keepFolder.isBlank()) {
                    throw new IllegalArgumentException("Keep directory is not configured");
                }
                String keepVariant = body.getOrDefault("variant", "normal");
                KeepResult result = photoService.keepImage(body.get("path"), keepFolder, suffixForKeepVariant(keepVariant));
                sendJson(exchange, 200, Map.of("status", "ok", "filename", result.filename(), "path", result.path()));
            }));

            server.createContext("/api/config", wrap(exchange -> {
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJson(exchange, 200, Map.of("keepDir", configStore.readKeepDir()));
                    return;
                }
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    Map<String, String> body = bodyAsMap(exchange);
                    String newKeepDir = body.getOrDefault("keepDir", "");
                    String saved = configStore.updateKeepDir(newKeepDir);
                    sendJson(exchange, 200, Map.of("keepDir", saved));
                    return;
                }
                throw new IllegalArgumentException("Unsupported method");
            }));

            server.createContext("/", exchange -> {
                try {
                    String path = exchange.getRequestURI().getPath();
                    if (path.equals("/")) {
                        path = "/index.html";
                    }
                    String resourcePath = "/web" + path;
                    try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
                        if (inputStream == null) {
                            sendText(exchange, 404, "Not found");
                            return;
                        }
                        byte[] content = inputStream.readAllBytes();
                        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
                        sendBinary(exchange, 200, staticContentType(path), content);
                    }
                } catch (Exception e) {
                    sendText(exchange, 500, e.getMessage());
                }
            });

            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
            LOGGER.info("Photo sorter started at http://localhost:{}", port);
            LOGGER.info("Use command 'serve -port {} -keepDir /path/to/keep'", port);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        try {
            ServePhotoSorterCommand command = new ServePhotoSorterCommand();
            new CmdLineParser(command).parseArgument(args);
            command.execute();
        } catch (Exception e) {
            LOGGER.error("Unable to start photo sorter", e);
            System.exit(2);
        }
    }

    @Override
    public String getName() {
        return "serve";
    }

    private HttpHandler wrap(HttpHandler handler) {
        return exchange -> {
            try {
                handler.handle(exchange);
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, Map.of("error", e.getMessage()));
            } catch (Exception e) {
                LOGGER.error("API error", e);
                sendJson(exchange, 500, Map.of("error", e.getMessage()));
            }
        };
    }

    private void requireMethod(HttpExchange exchange, String method) {
        if (!method.equalsIgnoreCase(exchange.getRequestMethod())) {
            throw new IllegalArgumentException("Method not allowed");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> bodyAsMap(HttpExchange exchange) throws IOException {
        try (InputStream body = exchange.getRequestBody()) {
            byte[] bytes = body.readAllBytes();
            if (bytes.length == 0) {
                return Map.of();
            }
            return objectMapper.readValue(bytes, Map.class);
        }
    }

    private void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(payload);
        sendBinary(exchange, status, "application/json; charset=utf-8", bytes);
    }

    private void sendText(HttpExchange exchange, int status, String text) throws IOException {
        sendBinary(exchange, status, "text/plain; charset=utf-8", text.getBytes(StandardCharsets.UTF_8));
    }

    private void sendBinary(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Map<String, String> query(URI uri) {
        Map<String, String> result = new HashMap<>();
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return result;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            result.put(key, value);
        }
        return result;
    }

    private String suffixForKeepVariant(String variant) {
        return switch (variant == null ? "normal" : variant) {
            case "normal" -> "_cover";
            case "back" -> "_back";
            case "instruction" -> "_instructions";
            case "divers" -> "_divers";
            default -> throw new IllegalArgumentException("Unknown keep variant: " + variant);
        };
    }

    private String staticContentType(String path) {
        if (path.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (path.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (path.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        return "application/octet-stream";
    }
}
