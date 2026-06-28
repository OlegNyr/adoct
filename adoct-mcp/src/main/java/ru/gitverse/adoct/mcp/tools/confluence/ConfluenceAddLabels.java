package ru.gitverse.adoct.mcp.tools.confluence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code confluence_add_labels} — добавляет метки странице Confluence. */
public final class ConfluenceAddLabels implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("pageId", "ID страницы", true)
                .arr("labels", "Массив меток", true)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_add_labels", "Добавить метки странице Confluence.", schema, args -> {
            String pageId = c.reqStr(args, "pageId");
            c.confluencePublish(args).addLabels(pageId, c.strList(args, "labels"));
            return c.ok(c.mapper().createObjectNode().put("labeled", pageId));
        });
    }
}
