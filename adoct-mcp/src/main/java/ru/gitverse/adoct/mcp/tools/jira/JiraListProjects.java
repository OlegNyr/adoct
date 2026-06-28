package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code jira_list_projects} — список проектов Jira. */
public final class JiraListProjects implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_list_projects", "Список проектов Jira.", schema, args ->
                c.ok(c.jira(args).listProjects()));
    }
}
