package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code jira_get_project_components} — компоненты проекта Jira. */
public final class JiraGetProjectComponents implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("projectKey", "Ключ проекта (по умолчанию из настроек)", false)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_get_project_components", "Компоненты проекта Jira.", schema, args ->
                c.ok(c.jira(args).getProjectComponents(c.requireProject(args))));
    }
}
