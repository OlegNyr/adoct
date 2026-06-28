package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code jira_create_issue} — создаёт задачу Jira (проект из аргумента или дефолтный). */
public final class JiraCreateIssue implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("projectKey", "Ключ проекта (по умолчанию из настроек)", false)
                .str("issueType", "Тип задачи (например Task, Story, Bug)", true)
                .str("summary", "Заголовок", true)
                .str("description", "Описание (необязательно)", false)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_create_issue", "Создать задачу Jira.", schema, args -> {
            ObjectNode payload = c.mapper().createObjectNode();
            ObjectNode fields = payload.putObject("fields");
            fields.putObject("project").put("key", c.requireProject(args));
            fields.putObject("issuetype").put("name", c.reqStr(args, "issueType"));
            fields.put("summary", c.reqStr(args, "summary"));
            c.putIfPresent(fields, "description", c.text(args, "description"));
            String key = c.jira(args).createIssue(payload);
            return c.ok(c.mapper().createObjectNode().put("key", key));
        });
    }
}
