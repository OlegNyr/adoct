package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code jira_get_issue} — читает задачу Jira по ключу (опц. набор полей). */
public final class JiraGetIssue implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("issueKey", "Ключ задачи, например ABC-123", true)
                .str("fields", "Поля через запятую или *all (по умолчанию базовый набор)", false)
                .str("host", "Хост Jira (если настроено несколько); иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_get_issue", "Прочитать задачу Jira по ключу.", schema, args ->
                c.ok(c.jira(args).getIssue(c.reqStr(args, "issueKey"), c.text(args, "fields"))));
    }
}
