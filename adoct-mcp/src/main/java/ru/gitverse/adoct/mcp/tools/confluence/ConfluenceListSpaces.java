package ru.gitverse.adoct.mcp.tools.confluence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code confluence_list_spaces} — список пространств Confluence (key + name) для выбора, где искать. */
public final class ConfluenceListSpaces implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .integer("limit", "Максимум пространств (по умолчанию 100)", false)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_list_spaces",
                "Список пространств Confluence (key + name).", schema, args ->
                c.ok(c.confluence(args).listSpaces(c.optInt(args, "limit", 100))));
    }
}
