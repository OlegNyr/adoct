package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code jira_update_sprint} — частично обновляет спринт Jira (имя/состояние/даты/цель). */
public final class JiraUpdateSprint implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("sprintId", "ID спринта", true)
                .str("name", "Новое название (необязательно)", false)
                .str("state", "Состояние: future|active|closed (необязательно)", false)
                .str("startDate", "Начало ISO-8601 (необязательно)", false)
                .str("endDate", "Окончание ISO-8601 (необязательно)", false)
                .str("goal", "Цель (необязательно)", false)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_update_sprint", "Обновить спринт Jira.", schema, args -> {
            ObjectNode payload = c.mapper().createObjectNode();
            c.putIfPresent(payload, "name", c.text(args, "name"));
            c.putIfPresent(payload, "state", c.text(args, "state"));
            c.putIfPresent(payload, "startDate", c.text(args, "startDate"));
            c.putIfPresent(payload, "endDate", c.text(args, "endDate"));
            c.putIfPresent(payload, "goal", c.text(args, "goal"));
            return c.ok(c.jira(args).updateSprint(c.reqStr(args, "sprintId"), payload));
        });
    }
}
