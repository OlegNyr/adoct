package io.github.adoct.mcp.tools.confluence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.adoct.mcp.InputSchema;
import io.github.adoct.mcp.McpTool;
import io.github.adoct.mcp.tools.Tool;
import io.github.adoct.mcp.tools.ToolContext;

/** {@code confluence_reply_to_comment} — отвечает на комментарий Confluence. */
public final class ConfluenceReplyToComment implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("pageId", "ID страницы", true)
                .str("parentCommentId", "ID комментария, на который отвечаем", true)
                .str("body", "Текст ответа", true)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_reply_to_comment", "Ответить на комментарий Confluence.", schema, args -> {
            String id = c.confluencePublish(args).addComment(
                    c.reqStr(args, "pageId"), c.reqStr(args, "parentCommentId"), c.paragraph(c.reqStr(args, "body")));
            return c.ok(c.mapper().createObjectNode().put("commentId", id));
        });
    }
}
