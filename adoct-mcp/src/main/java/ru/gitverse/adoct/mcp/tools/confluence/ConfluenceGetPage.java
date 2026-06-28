package ru.gitverse.adoct.mcp.tools.confluence;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;
import ru.gitverse.adoct.parser.confluence.ContentPage;

/**
 * {@code confluence_get_page} — читает страницу Confluence по ID. По умолчанию отдаёт storage-тело;
 * при {@code format=adoc} конвертирует страницу в AsciiDoc нашим движком и отдаёт текст в поле {@code adoc}.
 */
public final class ConfluenceGetPage implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("pageId", "ID страницы Confluence", true)
                .str("format", "storage (по умолчанию) или adoc — отдать страницу в AsciiDoc", false)
                .bool("fast", "Только для adoc: быстрый режим без доп. REST (ссылки резолвятся локально)", false)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_get_page",
                "Прочитать страницу Confluence по ID (storage-тело и вложения; format=adoc — в AsciiDoc).",
                schema, args -> {
            String pageId = c.reqStr(args, "pageId");
            String format = c.firstNonBlank(c.text(args, "format"), "storage");
            var client = c.confluence(args);
            ContentPage cp = client.getMainPage(pageId);
            ObjectNode out = c.mapper().createObjectNode();
            out.put("pageId", pageId);
            out.put("title", cp.title());
            out.put("url", cp.url());
            out.put("date", cp.date());
            if ("adoc".equalsIgnoreCase(format)) {
                out.put("format", "adoc");
                out.put("adoc", c.pageToAdoc(client, pageId, cp, c.optBool(args, "fast", false)));
            } else {
                out.put("format", "storage");
                out.put("storage", cp.content());
            }
            ArrayNode atts = out.putArray("attachments");
            cp.attachment().keySet().forEach(atts::add);
            return c.ok(out);
        });
    }
}
