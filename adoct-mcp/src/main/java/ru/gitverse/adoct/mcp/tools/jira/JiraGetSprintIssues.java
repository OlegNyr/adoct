package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code jira_get_sprint_issues} — задачи спринта Jira. */
public final class JiraGetSprintIssues implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("sprintId", "ID спринта", true)
                .integer("maxResults", "Лимит (1..100, по умолчанию 50)", false)
                .integer("startAt", "Смещение для пагинации (по умолчанию 0)", false)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_get_sprint_issues", "Задачи спринта Jira.", schema, args ->
                c.ok(c.jira(args).getSprintIssues(
                        c.reqStr(args, "sprintId"), c.optInt(args, "startAt", 0), c.optInt(args, "maxResults", 50))));
    }
}
