package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code jira_assign_issue} — назначить исполнителя задаче (по username из ростера). */
public final class JiraAssignIssue implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("issueKey", "Ключ задачи", true)
                .str("username", "Логин исполнителя (см. jira_list_team)", true)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_assign_issue", "Назначить исполнителя задаче Jira.", schema, args -> {
            String issueKey = c.reqStr(args, "issueKey");
            String username = c.reqStr(args, "username");
            c.jira(args).assignIssue(issueKey, username);
            ObjectNode out = c.mapper().createObjectNode();
            out.put("assigned", issueKey);
            out.put("assignee", username);
            return c.ok(out);
        });
    }
}
