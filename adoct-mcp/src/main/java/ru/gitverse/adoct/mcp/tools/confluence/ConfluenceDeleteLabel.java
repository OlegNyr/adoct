package ru.gitverse.adoct.mcp.tools.confluence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code confluence_delete_label} — удаляет метку у страницы Confluence. */
public final class ConfluenceDeleteLabel implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("pageId", "ID страницы", true)
                .str("name", "Имя метки", true)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_delete_label", "Удалить метку у страницы Confluence.", schema, args -> {
            c.confluencePublish(args).deleteLabel(c.reqStr(args, "pageId"), c.reqStr(args, "name"));
            return c.ok(c.mapper().createObjectNode().put("removed", true));
        });
    }
}
