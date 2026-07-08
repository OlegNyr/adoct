package io.github.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.adoct.mcp.InputSchema;
import io.github.adoct.mcp.McpTool;
import io.github.adoct.mcp.tools.Tool;
import io.github.adoct.mcp.tools.ToolContext;

/** {@code jira_get_transitions} — доступные переходы по workflow для задачи (id и название). */
public final class JiraGetTransitions implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("issueKey", "Ключ задачи", true)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_get_transitions",
                "Доступные переходы по workflow для задачи (id и название).", schema, args ->
                c.ok(c.jira(args).getTransitions(c.reqStr(args, "issueKey"))));
    }
}
