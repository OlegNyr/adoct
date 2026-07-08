package io.github.adoct.mcp.tools.confluence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.adoct.mcp.AtlassianKind;
import io.github.adoct.mcp.InputSchema;
import io.github.adoct.mcp.McpTool;
import io.github.adoct.mcp.tools.Tool;
import io.github.adoct.mcp.tools.ToolContext;

/**
 * {@code confluence_get_default_space} — возвращает настройки по умолчанию (хост Confluence и ключ
 * пространства), чтобы было понятно, где идёт поиск без явного {@code spaceKey}/{@code host}.
 */
public final class ConfluenceGetDefaultSpace implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_get_default_space",
                "Показать хост и пространство Confluence по умолчанию (где идёт поиск без spaceKey).",
                schema, args -> {
            ObjectNode out = c.mapper().createObjectNode();
            out.put("host", c.endpoint(args, AtlassianKind.CONFLUENCE).host());
            out.put("spaceKey", c.defaultConfluenceSpace().orElse(null));
            return c.ok(out);
        });
    }
}
