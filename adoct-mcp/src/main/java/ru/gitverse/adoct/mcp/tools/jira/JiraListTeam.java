package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

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
