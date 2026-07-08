package io.github.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.adoct.mcp.InputSchema;
import io.github.adoct.mcp.McpTool;
import io.github.adoct.mcp.tools.Tool;
import io.github.adoct.mcp.tools.ToolContext;

/** {@code jira_create_remote_issue_link} — добавляет внешнюю ссылку к задаче Jira. */
public final class JiraCreateRemoteIssueLink implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("issueKey", "Ключ задачи", true)
                .str("url", "URL внешней ссылки", true)
                .str("title", "Заголовок ссылки", true)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_create_remote_issue_link",
                "Добавить внешнюю ссылку к задаче Jira.", schema, args -> {
            ObjectNode payload = c.mapper().createObjectNode();
            ObjectNode object = payload.putObject("object");
            object.put("url", c.reqStr(args, "url"));
            object.put("title", c.reqStr(args, "title"));
            return c.ok(c.jira(args).createRemoteIssueLink(c.reqStr(args, "issueKey"), payload));
        });
    }
}
