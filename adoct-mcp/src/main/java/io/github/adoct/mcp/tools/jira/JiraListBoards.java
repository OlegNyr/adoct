package io.github.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.adoct.mcp.InputSchema;
import io.github.adoct.mcp.McpTool;
import io.github.adoct.mcp.tools.Tool;
import io.github.adoct.mcp.tools.ToolContext;

/** {@code jira_list_boards} — agile-доски Jira (опц. фильтр по проекту, иначе дефолтный). */
public final class JiraListBoards implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("projectKeyOrId", "Фильтр по проекту (по умолчанию из настроек)", false)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_list_boards", "Agile-доски Jira (опц. фильтр по проекту).", schema, args ->
                c.ok(c.jira(args).listBoards(
                        c.firstNonBlank(c.text(args, "projectKeyOrId"), c.defaultJiraProject().orElse(null)))));
    }
}
