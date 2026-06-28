package ru.gitverse.adoct.mcp.tools.confluence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code confluence_search} — поиск страниц Confluence по заголовку (CQL: точное, затем нечёткое). */
public final class ConfluenceSearch implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("query", "Заголовок страницы для поиска (CQL: точное, затем нечёткое)", true)
                .str("spaceKey", "Ограничить пространством (по умолчанию из настроек)", false)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_search", "Найти страницы Confluence по заголовку (CQL).", schema, args ->
                c.ok(c.confluence(args).search(c.reqStr(args, "query"),
                        c.firstNonBlank(c.text(args, "spaceKey"), c.defaultConfluenceSpace().orElse(null)))));
    }
}
