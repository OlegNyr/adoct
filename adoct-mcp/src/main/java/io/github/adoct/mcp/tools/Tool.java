package io.github.adoct.mcp.tools;

import io.github.adoct.mcp.McpTool;

/**
 * Фабрика одного MCP-инструмента. Каждый инструмент — отдельный класс в пакете {@code tools.jira} или
 * {@code tools.confluence}; он описывает имя, схему входа и обработчик, используя общий {@link ToolContext}.
 * Реестр {@link ToolRegistry} собирает все реализации в список {@link McpTool}.
 */
public interface Tool {

    /** Строит {@link McpTool} с доступом к клиентам и утилитам через {@code ctx}. */
    McpTool create(ToolContext ctx);
}
