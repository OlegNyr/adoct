package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code jira_list_assignable_users} — живой список пользователей, на которых можно назначать задачи проекта. */
public final class JiraListAssignableUsers implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("projectKey", "Ключ проекта (по умолчанию из настроек)", false)
                .str("query", "Фильтр по имени/логину (необязательно)", false)
                .integer("maxResults", "Лимит (1..100, по умолчанию 50)", false)
                .integer("startAt", "Смещение для пагинации (по умолчанию 0)", false)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_list_assignable_users",
                "Пользователи Jira, на которых можно назначать задачи проекта (живой список).", schema, args ->
                c.ok(c.jira(args).assignableUsers(c.requireProject(args), c.text(args, "query"),
                        c.optInt(args, "startAt", 0), c.optInt(args, "maxResults", 50))));
    }
}
