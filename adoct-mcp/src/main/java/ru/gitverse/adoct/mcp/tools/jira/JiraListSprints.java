package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code jira_list_sprints} — спринты доски Jira (опц. фильтр по состояниям). */
public final class JiraListSprints implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("boardId", "ID доски", true)
                .str("state", "Фильтр состояний через запятую: active,future,closed (необязательно)", false)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_list_sprints", "Спринты доски Jira.", schema, args ->
                c.ok(c.jira(args).listSprints(c.reqStr(args, "boardId"), c.text(args, "state"))));
    }
}
