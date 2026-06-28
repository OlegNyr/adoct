package ru.gitverse.adoct.mcp.tools.confluence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/**
 * {@code confluence_get_page_diff} — тела двух версий страницы (storage) для сравнения: историческая
 * {@code fromVersion} и {@code toVersion} (или текущая, если не задана). Сам diff делает вызывающий.
 */
public final class ConfluenceGetPageDiff implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("pageId", "ID страницы", true)
                .integer("fromVersion", "Номер исходной (исторической) версии", true)
                .integer("toVersion", "Номер целевой версии; без него — текущая", false)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_get_page_diff",
                "Тела двух версий страницы (storage) для сравнения.", schema, args -> {
            ru.gitverse.adoct.generate.confluence.ConfluenceClient client = c.confluencePublish(args);
            String pageId = c.reqStr(args, "pageId");
            JsonNode from = client.getPageStorageHistorical(pageId, c.reqInt(args, "fromVersion"));
            Integer toVersion = c.optInteger(args, "toVersion");
            JsonNode to = toVersion == null ? client.getPageStorageCurrent(pageId)
                    : client.getPageStorageHistorical(pageId, toVersion);
            ObjectNode out = c.mapper().createObjectNode();
            out.set("from", c.versionStorage(from));
            out.set("to", c.versionStorage(to));
            return c.ok(out);
        });
    }
}
