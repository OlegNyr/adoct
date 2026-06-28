package ru.gitverse.adoct.mcp.tools.confluence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code confluence_delete_attachment} — удаляет вложение Confluence по его content-id. */
public final class ConfluenceDeleteAttachment implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("attachmentId", "Content-ID вложения", true)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_delete_attachment", "Удалить вложение Confluence.", schema, args -> {
            String id = c.reqStr(args, "attachmentId");
            c.confluencePublish(args).deleteAttachment(id);
            return c.ok(c.mapper().createObjectNode().put("deleted", id));
        });
    }
}
