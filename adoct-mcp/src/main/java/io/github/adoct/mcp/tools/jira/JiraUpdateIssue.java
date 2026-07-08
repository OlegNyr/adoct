package io.github.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.adoct.mcp.InputSchema;
import io.github.adoct.mcp.McpTool;
import io.github.adoct.mcp.tools.Tool;
import io.github.adoct.mcp.tools.ToolContext;

/** {@code jira_update_issue} — обновляет поля задачи Jira (объект {@code fields}). */
public final class JiraUpdateIssue implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("issueKey", "Ключ задачи", true)
                .obj("fields", "Объект полей Jira для обновления (например {\"summary\":\"…\"})", true)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_update_issue", "Обновить поля задачи Jira.", schema, args -> {
            JsonNode fields = args.get("fields");
            if (fields == null || !fields.isObject()) {
                throw new IllegalArgumentException("Параметр fields должен быть объектом");
            }
            ObjectNode payload = c.mapper().createObjectNode();
            payload.set("fields", fields);
            String issueKey = c.reqStr(args, "issueKey");
            c.jira(args).updateIssue(issueKey, payload);
            return c.ok(c.mapper().createObjectNode().put("updated", issueKey));
        });
    }
}
