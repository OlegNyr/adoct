package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code jira_remove_issue_link} — удаляет связь между задачами Jira. */
public final class JiraRemoveIssueLink implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("linkId", "ID связи", true)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_remove_issue_link", "Удалить связь между задачами Jira.", schema, args -> {
            String linkId = c.reqStr(args, "linkId");
            c.jira(args).removeIssueLink(linkId);
            return c.ok(c.mapper().createObjectNode().put("removed", linkId));
        });
    }
}
