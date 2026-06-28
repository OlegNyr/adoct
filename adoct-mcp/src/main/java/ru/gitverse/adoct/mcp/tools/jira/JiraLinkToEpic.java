package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code jira_link_to_epic} — привязывает задачи к эпику Jira. */
public final class JiraLinkToEpic implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("epicKey", "Ключ эпика", true)
                .arr("issueKeys", "Массив ключей задач", true)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_link_to_epic", "Привязать задачи к эпику Jira.", schema, args -> {
            c.jira(args).linkToEpic(c.reqStr(args, "epicKey"), c.strList(args, "issueKeys"));
            return c.ok(c.mapper().createObjectNode().put("linked", true));
        });
    }
}
