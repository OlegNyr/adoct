package io.github.adoct.mcp.tools.confluence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.adoct.mcp.InputSchema;
import io.github.adoct.mcp.McpTool;
import io.github.adoct.mcp.tools.Tool;
import io.github.adoct.mcp.tools.ToolContext;

/** {@code confluence_move_page} — перемещает страницу относительно целевой ({@code append|above|below}). */
public final class ConfluenceMovePage implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("pageId", "ID перемещаемой страницы", true)
                .str("targetId", "ID целевой страницы", true)
                .str("position", "append|above|below (по умолчанию append)", false)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_move_page",
                "Переместить страницу Confluence относительно целевой.", schema, args -> {
            String position = c.firstNonBlank(c.text(args, "position"), "append");
            return c.ok(c.confluencePublish(args).movePage(
                    c.reqStr(args, "pageId"), position, c.reqStr(args, "targetId")));
        });
    }
}
