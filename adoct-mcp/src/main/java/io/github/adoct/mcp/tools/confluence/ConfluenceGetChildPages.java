package io.github.adoct.mcp.tools.confluence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.adoct.mcp.InputSchema;
import io.github.adoct.mcp.McpTool;
import io.github.adoct.mcp.tools.Tool;
import io.github.adoct.mcp.tools.ToolContext;

/** {@code confluence_get_child_pages} — ID прямых дочерних страниц. */
public final class ConfluenceGetChildPages implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("pageId", "ID родительской страницы", true)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_get_child_pages", "Получить ID прямых дочерних страниц.", schema, args ->
                c.ok(c.confluence(args).getChildPageIds(c.reqStr(args, "pageId"))));
    }
}
