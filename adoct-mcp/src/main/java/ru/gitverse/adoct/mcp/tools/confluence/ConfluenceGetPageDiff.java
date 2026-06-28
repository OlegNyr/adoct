package ru.gitverse.adoct.mcp.tools.confluence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/**
 * {@code confluence_get_page_diff} — тела двух версий страницы для сравнения: историческая
 * {@code fromVersion} и {@code toVersion} (или текущая, если не задана). По умолчанию storage;
 * при {@code format=adoc} тела версий конвертируются в AsciiDoc. Сам diff делает вызывающий.
 */
public final class ConfluenceGetPageDiff implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("pageId", "ID страницы", true)
                .integer("fromVersion", "Номер исходной (исторической) версии", true)
                .integer("toVersion", "Номер целевой версии; без него — текущая", false)
                .str("format", "storage (по умолчанию) или adoc — тела версий в AsciiDoc", false)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_get_page_diff",
                "Тела двух версий страницы для сравнения (storage; format=adoc — в AsciiDoc).", schema, args -> {
            ru.gitverse.adoct.generate.confluence.ConfluenceClient client = c.confluencePublish(args);
            String pageId = c.reqStr(args, "pageId");
            boolean adoc = "adoc".equalsIgnoreCase(c.text(args, "format"));
            JsonNode from = client.getPageStorageHistorical(pageId, c.reqInt(args, "fromVersion"));
            Integer toVersion = c.optInteger(args, "toVersion");
            JsonNode to = toVersion == null ? client.getPageStorageCurrent(pageId)
                    : client.getPageStorageHistorical(pageId, toVersion);
            ObjectNode out = c.mapper().createObjectNode();
            out.set("from", version(c, from, adoc));
            out.set("to", version(c, to, adoc));
            return c.ok(out);
        });
    }

    private ObjectNode version(ToolContext c, JsonNode content, boolean adoc) {
        if (!adoc) {
            return c.versionStorage(content);
        }
        ObjectNode o = c.mapper().createObjectNode();
        o.put("version", content.path("version").path("number").asInt());
        o.put("adoc", c.storageToAdoc(
                content.path("body").path("storage").path("value").asText(), content.path("title").asText()));
        return o;
    }
}
