package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code jira_get_workflow} — диаграмма состояний/переходов задач (PlantUML state) из настроек. */
public final class JiraGetWorkflow implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object().build();
        return new McpTool("jira_get_workflow",
                "Диаграмма состояний и переходов задач команды (PlantUML state).", schema, args ->
                c.ok(c.mapper().createObjectNode().put("plantuml", c.workflowDiagram())));
    }
}
