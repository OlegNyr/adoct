package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code jira_batch_create_issues} — массовое создание задач Jira (проект из элемента или дефолтный). */
public final class JiraBatchCreateIssues implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .arr("issues", "Массив задач: [{projectKey?,issueType,summary,description?}]", true)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_batch_create_issues", "Массово создать задачи Jira.", schema, args -> {
            JsonNode items = args.get("issues");
            if (items == null || !items.isArray() || items.isEmpty()) {
                throw new IllegalArgumentException("Параметр issues должен быть непустым массивом");
            }
            ObjectNode payload = c.mapper().createObjectNode();
            ArrayNode updates = payload.putArray("issueUpdates");
            for (JsonNode it : items) {
                ObjectNode fields = updates.addObject().putObject("fields");
                String project = c.firstNonBlank(c.text(it, "projectKey"), c.defaultJiraProject().orElse(null));
                if (project == null || project.isBlank()) {
                    throw new IllegalArgumentException("Не задан projectKey и нет проекта по умолчанию");
                }
                fields.putObject("project").put("key", project);
                fields.putObject("issuetype").put("name", c.reqStr(it, "issueType"));
                fields.put("summary", c.reqStr(it, "summary"));
                c.putIfPresent(fields, "description", c.text(it, "description"));
            }
            return c.ok(c.jira(args).createIssuesBulk(payload));
        });
    }
}
