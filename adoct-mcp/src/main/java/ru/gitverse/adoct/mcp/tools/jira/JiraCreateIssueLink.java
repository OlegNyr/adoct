package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code jira_create_issue_link} — связывает две задачи Jira. */
public final class JiraCreateIssueLink implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("type", "Название типа связи (например Blocks, Relates)", true)
                .str("inwardIssue", "Ключ inward-задачи", true)
                .str("outwardIssue", "Ключ outward-задачи", true)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_create_issue_link", "Связать две задачи Jira.", schema, args -> {
            ObjectNode payload = c.mapper().createObjectNode();
            payload.putObject("type").put("name", c.reqStr(args, "type"));
            payload.putObject("inwardIssue").put("key", c.reqStr(args, "inwardIssue"));
            payload.putObject("outwardIssue").put("key", c.reqStr(args, "outwardIssue"));
            c.jira(args).createIssueLink(payload);
            return c.ok(c.mapper().createObjectNode().put("linked", true));
        });
    }
}
