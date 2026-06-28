package ru.gitverse.adoct.mcp;

/**
 * Результат вызова тула: текст (обычно JSON) и флаг ошибки. Соответствует MCP {@code CallToolResult}
 * с единственным {@code TextContent}.
 */
public record ToolResult(String text, boolean isError) {

    public static ToolResult ok(String text) {
        return new ToolResult(text, false);
    }

    public static ToolResult error(String message) {
        return new ToolResult(message, true);
    }
}
