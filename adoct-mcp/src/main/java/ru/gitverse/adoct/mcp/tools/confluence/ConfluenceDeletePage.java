package ru.gitverse.adoct.mcp.tools.confluence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code confluence_delete_page} — удаляет страницу Confluence. */
public final class ConfluenceDeletePage implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("pageId", "ID страницы", true)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_delete_page", "Удалить страницу Confluence.", schema, args -> {
            String pageId = c.reqStr(args, "pageId");
            c.confluencePublish(args).deletePage(pageId);
            return c.ok(c.mapper().createObjectNode().put("deleted", pageId));
        });
    }
}
