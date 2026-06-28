package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code jira_create_version} — создаёт версию проекта Jira. */
public final class JiraCreateVersion implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("projectKey", "Ключ проекта (по умолчанию из настроек)", false)
                .str("name", "Название версии", true)
                .str("description", "Описание (необязательно)", false)
                .bool("released", "Помечена выпущенной (по умолчанию false)", false)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_create_version", "Создать версию проекта Jira.", schema, args -> {
            ObjectNode payload = c.mapper().createObjectNode();
            payload.put("project", c.requireProject(args));
            payload.put("name", c.reqStr(args, "name"));
            c.putIfPresent(payload, "description", c.text(args, "description"));
            payload.put("released", c.optBool(args, "released", false));
            return c.ok(c.jira(args).createVersion(payload));
        });
    }
}
