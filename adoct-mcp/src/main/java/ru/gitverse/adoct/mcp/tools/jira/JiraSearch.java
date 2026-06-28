package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code jira_search} — поиск задач по JQL. */
public final class JiraSearch implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("jql", "JQL-запрос", true)
                .integer("maxResults", "Лимит результатов (1..100, по умолчанию 50)", false)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_search", "Найти задачи Jira по JQL.", schema, args ->
                c.ok(c.jira(args).searchJql(c.reqStr(args, "jql"), c.optInt(args, "maxResults", 50))));
    }
}
