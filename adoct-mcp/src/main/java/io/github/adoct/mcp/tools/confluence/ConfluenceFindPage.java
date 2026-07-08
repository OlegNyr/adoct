package io.github.adoct.mcp.tools.confluence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.adoct.mcp.InputSchema;
import io.github.adoct.mcp.McpTool;
import io.github.adoct.mcp.tools.Tool;
import io.github.adoct.mcp.tools.ToolContext;

import java.util.Optional;

/** {@code confluence_find_page} — ищет ID страницы по пространству и точному заголовку. */
public final class ConfluenceFindPage implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("spaceKey", "Ключ пространства (по умолчанию из настроек)", false)
                .str("title", "Точный заголовок страницы", true)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_find_page",
                "Найти ID страницы по пространству и точному заголовку.", schema, args -> {
            Optional<String> id = c.confluence(args).findPageId(c.requireSpace(args), c.reqStr(args, "title"));
            return c.ok(c.mapper().createObjectNode().put("pageId", id.orElse(null)));
        });
    }
}
