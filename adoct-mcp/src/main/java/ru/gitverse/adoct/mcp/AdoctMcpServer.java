package ru.gitverse.adoct.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;
import ru.gitverse.adoct.mcp.tools.ToolRegistry;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP-сервер поверх JDK {@link HttpServer}: один эндпоинт {@code POST /mcp} (Streamable HTTP, ответ —
 * {@code application/json}). Протокол обрабатывает транспорт-агностичный {@link McpDispatcher};
 * этот класс — только HTTP-транспорт. Без внешнего MCP SDK и веб-контейнера.
 */
@Slf4j
public final class AdoctMcpServer implements AutoCloseable {

    private static final byte[] EMPTY = new byte[0];

    private final McpDispatcher dispatcher;
    private HttpServer httpServer;

    /** Сервер из готового списка инструментов (используется CLI, в т.ч. native-сборкой). */
    public AdoctMcpServer(List<McpTool> tools, String serverName, String serverVersion) {
        this.dispatcher = new McpDispatcher(tools, serverName, serverVersion);
    }

    /** Удобный конструктор: полный набор инструментов из {@link ToolRegistry} (плагин/JVM). */
    public AdoctMcpServer(EndpointSupplier endpoints, String serverName, String serverVersion) {
        this(new ToolRegistry(endpoints).tools(), serverName, serverVersion);
    }

    /** Поднимает HTTP-сервер на {@code host:port}. {@code port == 0} → произвольный свободный порт. */
    public synchronized void start(String host, int port) throws IOException {
        if (httpServer != null) {
            return;
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/mcp", this::handle);
        AtomicInteger seq = new AtomicInteger();
        server.setExecutor(Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "adoct-mcp-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        }));
        server.start();
        this.httpServer = server;
        log.info("MCP server listening on http://{}:{}/mcp", host, server.getAddress().getPort());
    }

    /** Фактический порт (после {@link #start}); -1 если не запущен. */
    public synchronized int port() {
        return httpServer == null ? -1 : httpServer.getAddress().getPort();
    }

    @Override
    public synchronized void close() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }

    // ---- HTTP ----

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                send(exchange, 405, EMPTY, null);
                return;
            }
            byte[] in = exchange.getRequestBody().readAllBytes();
            JsonNode message;
            try {
                message = dispatcher.mapper().readTree(in);
            } catch (Exception parse) {
                send(exchange, 200, bytes(dispatcher.error(null, -32700, "Parse error")), null);
                return;
            }

            if (message != null && message.isArray()) {
                ArrayNode out = dispatcher.mapper().createArrayNode();
                for (JsonNode m : message) {
                    ObjectNode r = dispatcher.dispatch(m);
                    if (r != null) {
                        out.add(r);
                    }
                }
                if (out.isEmpty()) {
                    send(exchange, 202, EMPTY, null);
                } else {
                    send(exchange, 200, bytes(out), null);
                }
                return;
            }

            String method = message == null ? null : message.path("method").asText(null);
            String sessionId = "initialize".equals(method) ? UUID.randomUUID().toString() : null;
            ObjectNode response = dispatcher.dispatch(message);
            if (response == null) {
                send(exchange, 202, EMPTY, sessionId);
            } else {
                send(exchange, 200, bytes(response), sessionId);
            }
        } catch (Exception e) {
            log.warn("MCP request failed", e);
            try {
                send(exchange, 500, EMPTY, null);
            } catch (IOException ignore) {
                // соединение уже закрыто
            }
        }
    }

    private byte[] bytes(JsonNode node) throws IOException {
        return dispatcher.mapper().writeValueAsBytes(node);
    }

    private void send(HttpExchange exchange, int status, byte[] body, String sessionId) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        if (sessionId != null) {
            exchange.getResponseHeaders().set("Mcp-Session-Id", sessionId);
        }
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
        exchange.close();
    }
}
