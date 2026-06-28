package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code jira_delete_issue} — удаляет задачу Jira (опц. вместе с подзадачами). */
public final class JiraDeleteIssue implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("issueKey", "Ключ задачи", true)
                .bool("deleteSubtasks", "Удалять подзадачи (по умолчанию false)", false)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_delete_issue", "Удалить задачу Jira.", schema, args -> {
            String key = c.reqStr(args, "issueKey");
            c.jira(args).deleteIssue(key, c.optBool(args, "deleteSubtasks", false));
            return c.ok(c.mapper().createObjectNode().put("deleted", key));
        });
    }
}
