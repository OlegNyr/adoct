package ru.gitverse.adoct.mcp.tools.confluence;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;
import ru.gitverse.adoct.parser.confluence.ContentPage;

/** {@code confluence_get_page} — читает страницу Confluence (storage-тело и список вложений) по ID. */
public final class ConfluenceGetPage implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("pageId", "ID страницы Confluence", true)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_get_page",
                "Прочитать страницу Confluence (storage-тело и вложения) по ID.", schema, args -> {
            String pageId = c.reqStr(args, "pageId");
            ContentPage cp = c.confluence(args).getMainPage(pageId);
            ObjectNode out = c.mapper().createObjectNode();
            out.put("pageId", pageId);
            out.put("title", cp.title());
            out.put("url", cp.url());
            out.put("date", cp.date());
            out.put("storage", cp.content());
            ArrayNode atts = out.putArray("attachments");
            cp.attachment().keySet().forEach(atts::add);
            return c.ok(out);
        });
    }
}
