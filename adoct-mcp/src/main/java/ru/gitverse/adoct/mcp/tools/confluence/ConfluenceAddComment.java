package ru.gitverse.adoct.mcp.tools.confluence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code confluence_add_comment} — добавляет комментарий к странице Confluence. */
public final class ConfluenceAddComment implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("pageId", "ID страницы", true)
                .str("body", "Текст комментария", true)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_add_comment", "Добавить комментарий к странице Confluence.", schema, args -> {
            String id = c.confluencePublish(args).addComment(
                    c.reqStr(args, "pageId"), null, c.paragraph(c.reqStr(args, "body")));
            return c.ok(c.mapper().createObjectNode().put("commentId", id));
        });
    }
}
