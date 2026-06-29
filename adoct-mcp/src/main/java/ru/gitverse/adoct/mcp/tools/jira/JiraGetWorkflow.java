package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/**
 * {@code jira_get_workflow} — диаграммы состояний/переходов задач по типам (PlantUML state) из настроек.
 * Состояние привязано к типу задачи (как и шаблон).
 */
public final class JiraGetWorkflow implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("issueType", "Тип задачи; иначе — все типы с заданной диаграммой", false)
                .build();
        return new McpTool("jira_get_workflow",
                "Диаграммы состояний и переходов задач по типам (PlantUML state).", schema, args -> {
            String filter = c.text(args, "issueType");
            ArrayNode out = c.mapper().createArrayNode();
            c.templates().stream()
                    .filter(t -> t.workflow() != null && !t.workflow().isBlank())
                    .filter(t -> filter == null || filter.isBlank() || filter.equalsIgnoreCase(t.issueType()))
                    .forEach(t -> {
                        ObjectNode n = out.addObject();
                        n.put("issueType", t.issueType());
                        n.put("plantuml", t.workflow());
                    });
            return c.ok(out);
        });
    }
}
