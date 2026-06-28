package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code jira_add_worklog} — добавляет запись о работе к задаче Jira. */
public final class JiraAddWorklog implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("issueKey", "Ключ задачи", true)
                .str("timeSpent", "Затраченное время, например 2h 30m", true)
                .str("comment", "Комментарий (необязательно)", false)
                .str("started", "Время начала ISO-8601 (необязательно)", false)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_add_worklog", "Добавить запись о работе к задаче Jira.", schema, args -> {
            ObjectNode payload = c.mapper().createObjectNode();
            payload.put("timeSpent", c.reqStr(args, "timeSpent"));
            c.putIfPresent(payload, "comment", c.text(args, "comment"));
            c.putIfPresent(payload, "started", c.text(args, "started"));
            return c.ok(c.jira(args).addWorklog(c.reqStr(args, "issueKey"), payload));
        });
    }
}
