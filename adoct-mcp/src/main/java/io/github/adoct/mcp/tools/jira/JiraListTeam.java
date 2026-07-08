package io.github.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.adoct.mcp.InputSchema;
import io.github.adoct.mcp.McpTool;
import io.github.adoct.mcp.tools.Tool;
import io.github.adoct.mcp.tools.ToolContext;

/** {@code jira_list_team} — сконфигурированный ростер команды (username/имя/роль) для привязки задач. */
public final class JiraListTeam implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object().build();
        return new McpTool("jira_list_team",
                "Список участников команды (ростер из настроек) для назначения задач.", schema, args ->
                c.ok(c.team()));
    }
}
