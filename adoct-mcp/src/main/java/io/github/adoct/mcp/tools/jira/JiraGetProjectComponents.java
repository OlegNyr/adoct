package io.github.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.adoct.mcp.InputSchema;
import io.github.adoct.mcp.McpTool;
import io.github.adoct.mcp.tools.Tool;
import io.github.adoct.mcp.tools.ToolContext;

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
