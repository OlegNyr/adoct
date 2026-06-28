package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code jira_get_current_user} — текущий пользователь Jira (владелец токена). */
public final class JiraGetCurrentUser implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_get_current_user", "Текущий пользователь Jira (владелец токена).", schema, args ->
                c.ok(c.jira(args).getCurrentUser()));
    }
}
