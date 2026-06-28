package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code jira_add_comment} — добавляет комментарий к задаче Jira. */
public final class JiraAddComment implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("issueKey", "Ключ задачи", true)
                .str("body", "Текст комментария", true)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_add_comment", "Добавить комментарий к задаче Jira.", schema, args ->
                c.ok(c.jira(args).addComment(c.reqStr(args, "issueKey"), c.reqStr(args, "body"))));
    }
}
