package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code jira_transition_issue} — переводит задачу по переходу workflow. */
public final class JiraTransitionIssue implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("issueKey", "Ключ задачи", true)
                .str("transitionId", "ID перехода (см. jira_get_transitions)", true)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_transition_issue",
                "Перевести задачу Jira по переходу workflow.", schema, args -> {
            String issueKey = c.reqStr(args, "issueKey");
            String transitionId = c.reqStr(args, "transitionId");
            c.jira(args).transitionIssue(issueKey, transitionId);
            ObjectNode out = c.mapper().createObjectNode();
            out.put("transitioned", issueKey);
            out.put("transitionId", transitionId);
            return c.ok(out);
        });
    }
}
