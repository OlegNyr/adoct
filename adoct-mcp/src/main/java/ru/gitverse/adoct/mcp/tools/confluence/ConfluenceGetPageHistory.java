package ru.gitverse.adoct.mcp.tools.confluence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code confluence_get_page_history} — список версий страницы Confluence. */
public final class ConfluenceGetPageHistory implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("pageId", "ID страницы", true)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_get_page_history", "Версии страницы Confluence.", schema, args ->
                c.ok(c.confluencePublish(args).getVersions(c.reqStr(args, "pageId"))));
    }
}
