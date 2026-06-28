package ru.gitverse.adoct.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;
import ru.gitverse.adoct.mcp.tools.ToolCatalog;
import ru.gitverse.adoct.parser.confluence.ObjectMapperExt;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP-сервер поверх JDK {@link HttpServer}: минимальная реализация JSON-RPC 2.0 / MCP
 * (методы {@code initialize}, {@code tools/list}, {@code tools/call}, {@code ping}) на одном
 * эндпоинте {@code POST /mcp} (Streamable HTTP, ответ — {@code application/json}). Без внешнего
 * MCP SDK и веб-контейнера. Read-only набор тулов — см. {@link ToolCatalog}.
 */
@Slf4j
public final class AdoctMcpServer implements AutoCloseable {

    private static final String PROTOCOL_VERSION = "2024-11-05";
    private static final byte[] EMPTY = new byte[0];

    /** Промпт-персона: продукт-овнер с навыками senior Java-разработчика. */
    private static final String PRODUCT_OWNER_PROMPT = """
            Ты — продукт-овнер с навыками senior Java-разработчика. Ведёшь продукт, проект и команду
            через Jira и Confluence (Server/Data Center).

            Принципы работы:
            - Сначала разберись в контексте: ищи задачи (jira_search по JQL) и документацию
              (confluence_search, confluence_get_page), прежде чем предлагать решения.
            - Формулируй понятные пользовательские истории и критерии приёмки; разбивай эпики на задачи.
            - Управляй беклогом и статусами: jira_create_issue, jira_update_issue,
              jira_get_transitions + jira_transition_issue, jira_add_comment.
            - Документацию веди в Confluence; для оффлайн-работы выгружай дерево страниц в AsciiDoc
              (confluence_export_tree_to_adoc) и публикуй правки обратно (confluence_publish_adoc).
            - Мысли как инженер: учитывай реализуемость, технический долг и риски; давай оценки и
              приоритеты с обоснованием.
            - Перед изменяющими действиями (создание/обновление/переход задач, публикация страниц)
              кратко проговаривай намерение.

            Отвечай по делу, на языке пользователя.""";

    private final ObjectMapper mapper = ObjectMapperExt.INSTANT;
    private final String serverName;
    private final String serverVersion;
    private final List<McpTool> tools;
    private final Map<String, McpTool> byName = new LinkedHashMap<>();
    private final List<Prompt> prompts = List.of(
            new Prompt("product_owner",
                    "Персона: продукт-овнер с навыками senior Java-разработчика (управление продуктом/командой).",
                    PRODUCT_OWNER_PROMPT));

    private record Prompt(String name, String description, String text) {
    }

    private HttpServer httpServer;

    public AdoctMcpServer(EndpointSupplier endpoints, String serverName, String serverVersion) {
        this.serverName = serverName;
        this.serverVersion = serverVersion;
        this.tools = new ToolCatalog(endpoints).tools();
        for (McpTool tool : tools) {
            byName.put(tool.name(), tool);
        }
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
                message = mapper.readTree(in);
            } catch (Exception parse) {
                send(exchange, 200, bytes(error(null, -32700, "Parse error")), null);
                return;
            }

            if (message != null && message.isArray()) {
                ArrayNode out = mapper.createArrayNode();
                for (JsonNode m : message) {
                    ObjectNode r = dispatch(m);
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
            ObjectNode response = dispatch(message);
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

    /** Обрабатывает одно JSON-RPC сообщение. Возвращает ответ или {@code null} для нотификаций. */
    private ObjectNode dispatch(JsonNode message) {
        if (message == null) {
            return error(null, -32600, "Invalid Request");
        }
        String method = message.path("method").asText(null);
        JsonNode id = message.get("id");
        boolean notification = id == null || id.isNull();
        if (method == null) {
            return notification ? null : error(id, -32600, "Invalid Request");
        }
        try {
            return switch (method) {
                case "initialize" -> result(id, initializeResult(message.path("params")));
                case "ping" -> result(id, mapper.createObjectNode());
                case "tools/list" -> result(id, toolsList());
                case "tools/call" -> result(id, toolsCall(message.path("params")));
                case "prompts/list" -> result(id, promptsList());
                case "prompts/get" -> result(id, promptsGet(message.path("params")));
                case "notifications/initialized" -> null;
                default -> notification ? null : error(id, -32601, "Method not found: " + method);
            };
        } catch (Exception e) {
            return notification ? null : error(id, -32603, message(e));
        }
    }

    private ObjectNode initializeResult(JsonNode params) {
        ObjectNode r = mapper.createObjectNode();
        String requested = params.path("protocolVersion").asText(null);
        r.put("protocolVersion", requested == null || requested.isBlank() ? PROTOCOL_VERSION : requested);
        ObjectNode caps = r.putObject("capabilities");
        caps.putObject("tools");
        caps.putObject("prompts");
        ObjectNode info = r.putObject("serverInfo");
        info.put("name", serverName);
        info.put("version", serverVersion);
        return r;
    }

    private ObjectNode toolsList() {
        ObjectNode r = mapper.createObjectNode();
        ArrayNode arr = r.putArray("tools");
        for (McpTool tool : tools) {
            ObjectNode tn = arr.addObject();
            tn.put("name", tool.name());
            tn.put("description", tool.description());
            tn.set("inputSchema", tool.inputSchema());
        }
        return r;
    }

    private ObjectNode toolsCall(JsonNode params) {
        String name = params.path("name").asText(null);
        JsonNode arguments = params.path("arguments");
        if (arguments == null || arguments.isMissingNode() || arguments.isNull()) {
            arguments = mapper.createObjectNode();
        }
        ObjectNode r = mapper.createObjectNode();
        ArrayNode content = r.putArray("content");
        McpTool tool = byName.get(name);
        if (tool == null) {
            content.addObject().put("type", "text").put("text", "Unknown tool: " + name);
            r.put("isError", true);
            return r;
        }
        ToolResult res;
        try {
            res = tool.call(arguments);
        } catch (Exception e) {
            res = ToolResult.error(message(e));
        }
        content.addObject().put("type", "text").put("text", res.text());
        r.put("isError", res.isError());
        return r;
    }

    private ObjectNode promptsList() {
        ObjectNode r = mapper.createObjectNode();
        ArrayNode arr = r.putArray("prompts");
        for (Prompt p : prompts) {
            ObjectNode n = arr.addObject();
            n.put("name", p.name());
            n.put("description", p.description());
        }
        return r;
    }

    private ObjectNode promptsGet(JsonNode params) {
        String name = params.path("name").asText(null);
        Prompt prompt = prompts.stream()
                .filter(p -> p.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown prompt: " + name));
        ObjectNode r = mapper.createObjectNode();
        r.put("description", prompt.description());
        ObjectNode content = mapper.createObjectNode();
        content.put("type", "text");
        content.put("text", prompt.text());
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", "user");
        msg.set("content", content);
        r.putArray("messages").add(msg);
        return r;
    }

    // ---- JSON-RPC envelope ----

    private ObjectNode result(JsonNode id, JsonNode payload) {
        ObjectNode r = mapper.createObjectNode();
        r.put("jsonrpc", "2.0");
        r.set("id", id);
        r.set("result", payload);
        return r;
    }

    private ObjectNode error(JsonNode id, int code, String msg) {
        ObjectNode r = mapper.createObjectNode();
        r.put("jsonrpc", "2.0");
        if (id == null) {
            r.putNull("id");
        } else {
            r.set("id", id);
        }
        ObjectNode e = r.putObject("error");
        e.put("code", code);
        e.put("message", msg == null ? "error" : msg);
        return r;
    }

    private byte[] bytes(JsonNode node) throws IOException {
        return mapper.writeValueAsBytes(node);
    }

    private static String message(Throwable e) {
        return e.getMessage() != null ? e.getMessage() : e.toString();
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
