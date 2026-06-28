package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code jira_add_watcher} — добавляет наблюдателя к задаче Jira. */
public final class JiraAddWatcher implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("issueKey", "Ключ задачи", true)
                .str("username", "Имя пользователя", true)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_add_watcher", "Добавить наблюдателя к задаче Jira.", schema, args -> {
            c.jira(args).addWatcher(c.reqStr(args, "issueKey"), c.reqStr(args, "username"));
            return c.ok(c.mapper().createObjectNode().put("added", true));
        });
    }
}
