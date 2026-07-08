package io.github.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.adoct.mcp.InputSchema;
import io.github.adoct.mcp.McpTool;
import io.github.adoct.mcp.tools.Tool;
import io.github.adoct.mcp.tools.ToolContext;

/** {@code jira_add_issues_to_sprint} — перемещает задачи в спринт Jira. */
public final class JiraAddIssuesToSprint implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("sprintId", "ID спринта", true)
                .arr("issueKeys", "Массив ключей задач", true)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_add_issues_to_sprint", "Переместить задачи в спринт Jira.", schema, args -> {
            c.jira(args).addIssuesToSprint(c.reqStr(args, "sprintId"), c.strList(args, "issueKeys"));
            return c.ok(c.mapper().createObjectNode().put("moved", true));
        });
    }
}
