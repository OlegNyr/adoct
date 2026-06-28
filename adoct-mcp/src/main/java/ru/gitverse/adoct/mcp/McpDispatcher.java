package ru.gitverse.adoct.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.parser.confluence.ObjectMapperExt;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Транспорт-агностичное ядро MCP: JSON-RPC 2.0 диспетчер (методы {@code initialize}, {@code tools/list},
 * {@code tools/call}, {@code prompts/*}, {@code ping}). Используется и HTTP-сервером ({@link AdoctMcpServer}),
 * и stdio-режимом CLI. Не знает про транспорт — принимает JSON-сообщение, возвращает ответ или {@code null}
 * для нотификаций.
 */
public final class McpDispatcher {

    private static final String PROTOCOL_VERSION = "2024-11-05";

    /** Промпт-персона: продукт-овнер с навыками senior Java-разработчика. */
    private static final String PRODUCT_OWNER_PROMPT = """
            Ты — продукт-овнер с навыками senior Java-разработчика. Ведёшь продукт, проект и команду
            через Jira и Confluence (Server/Data Center).

            Принципы работы:
            - Сначала разберись в контексте: ищи задачи (jira_search по JQL) и документацию
              (confluence_search, confluence_get_page), прежде чем предлагать решения.
            - Формулируй понятные пользовательские истории и критерии приёмки; разбивай эпики на задачи.
            - Мысли как инженер: учитывай реализуемость, технический долг и риски; давай оценки и
              приоритеты с обоснованием.
            - Перед изменяющими действиями (создание/обновление/переход задач, публикация страниц)
              кратко проговаривай намерение.

            Создание задачи — сначала шаблон и команда:
            1. jira_list_templates — возьми текст подходящего шаблона (это свободный текст, не схема;
               распарси сам: issueType, заготовку summary, описание/чек-лист, метки).
            2. jira_list_team (ростер username/имя/роль) или jira_list_assignable_users — выбери исполнителя.
            3. (опц.) jira_get_workflow и jira_get_project_statuses — пойми доступные состояния/переходы.
            4. jira_create_issue по шаблону (projectKey подставится из настроек, если не задан).
            5. jira_assign_issue по username из ростера; при необходимости jira_link_to_epic /
               jira_create_issue_link / jira_add_issues_to_sprint.

            Статусы — через jira_get_transitions → jira_transition_issue (переход задаётся id, не именем).

            Confluence: для подачи страницы в контекст бери confluence_get_page format=adoc fast=true;
            правки публикуй round-trip через confluence_publish_adoc (связь страница↔файл по :confluency-id:);
            дерево страниц оффлайн — confluence_export_tree_to_adoc.

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

    public McpDispatcher(List<McpTool> tools, String serverName, String serverVersion) {
        this.tools = tools;
        this.serverName = serverName;
        this.serverVersion = serverVersion;
        for (McpTool tool : tools) {
            byName.put(tool.name(), tool);
        }
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    /** Обрабатывает одно JSON-RPC сообщение. Возвращает ответ или {@code null} для нотификаций. */
    public ObjectNode dispatch(JsonNode message) {
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

    public ObjectNode error(JsonNode id, int code, String msg) {
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

    private ObjectNode result(JsonNode id, JsonNode payload) {
        ObjectNode r = mapper.createObjectNode();
        r.put("jsonrpc", "2.0");
        r.set("id", id);
        r.set("result", payload);
        return r;
    }

    private static String message(Throwable e) {
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }
}
