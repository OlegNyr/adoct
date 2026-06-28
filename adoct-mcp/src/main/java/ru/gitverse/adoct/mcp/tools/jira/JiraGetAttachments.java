package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code jira_get_attachments} — метаданные вложений задачи Jira. */
public final class JiraGetAttachments implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("issueKey", "Ключ задачи", true)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_get_attachments", "Метаданные вложений задачи Jira.", schema, args ->
                c.ok(c.jira(args).getAttachmentsMeta(c.reqStr(args, "issueKey"))));
    }
}
