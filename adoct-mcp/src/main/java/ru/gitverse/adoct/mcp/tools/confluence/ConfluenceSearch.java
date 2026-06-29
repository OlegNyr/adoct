package ru.gitverse.adoct.mcp.tools.confluence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/**
 * {@code confluence_search} — поиск страниц Confluence по заголовку и тексту (CQL-каскад:
 * {@code title = } → {@code title ~ } → {@code text ~ }).
 */
public final class ConfluenceSearch implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("query", "Строка поиска: ищется по заголовку и по тексту страницы (CQL-каскад)", true)
                .str("spaceKey", "Ограничить пространством (ключ или URL); по умолчанию — по всем пространствам", false)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_search",
                "Найти страницы Confluence по заголовку и тексту (CQL: title=, затем title~, затем text~). "
                        + "По умолчанию ищет по всем пространствам; результаты содержат ключ пространства.",
                schema, args -> {
            String spaceArg = c.text(args, "spaceKey");
            String spaceKey = spaceArg == null || spaceArg.isBlank() ? null : ToolContext.spaceKeyOf(spaceArg);
            return c.ok(c.confluence(args).searchText(c.reqStr(args, "query"), spaceKey));
        });
    }
}
