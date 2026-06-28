package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code jira_create_sprint} — создаёт спринт Jira на доске. */
public final class JiraCreateSprint implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("name", "Название спринта", true)
                .str("boardId", "ID доски (originBoardId)", true)
                .str("startDate", "Начало ISO-8601 (необязательно)", false)
                .str("endDate", "Окончание ISO-8601 (необязательно)", false)
                .str("goal", "Цель спринта (необязательно)", false)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_create_sprint", "Создать спринт Jira.", schema, args -> {
            ObjectNode payload = c.mapper().createObjectNode();
            payload.put("name", c.reqStr(args, "name"));
            payload.put("originBoardId", c.reqInt(args, "boardId"));
            c.putIfPresent(payload, "startDate", c.text(args, "startDate"));
            c.putIfPresent(payload, "endDate", c.text(args, "endDate"));
            c.putIfPresent(payload, "goal", c.text(args, "goal"));
            return c.ok(c.jira(args).createSprint(payload));
        });
    }
}
