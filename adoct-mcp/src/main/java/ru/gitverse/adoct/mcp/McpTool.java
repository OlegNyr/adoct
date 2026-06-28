package ru.gitverse.adoct.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Описание MCP-тула: имя, описание, JSON-схема входа и обработчик. Регистрируется в каталоге и
 * вызывается сервером на {@code tools/call}.
 */
public final class McpTool {

    /** Обработчик вызова тула: получает аргументы (объект {@code arguments}) и возвращает результат. */
    @FunctionalInterface
    public interface Handler {
        ToolResult call(JsonNode arguments) throws Exception;
    }

    private final String name;
    private final String description;
    private final ObjectNode inputSchema;
    private final Handler handler;

    public McpTool(String name, String description, ObjectNode inputSchema, Handler handler) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.handler = handler;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public ObjectNode inputSchema() {
        return inputSchema;
    }

    public ToolResult call(JsonNode arguments) throws Exception {
        return handler.call(arguments);
    }
}
