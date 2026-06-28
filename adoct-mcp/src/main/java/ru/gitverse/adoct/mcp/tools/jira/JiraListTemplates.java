package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/**
 * {@code jira_list_templates} — именованные шаблоны задач (свободный текст). Ассистент читает шаблон
 * и сам формирует вызов {@code jira_create_issue}; сервер шаблоны не интерпретирует.
 */
public final class JiraListTemplates implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object().build();
        return new McpTool("jira_list_templates",
                "Шаблоны задач (имя + текст). Используй текст шаблона, чтобы собрать jira_create_issue.",
                schema, args -> c.ok(c.templates()));
    }
}
