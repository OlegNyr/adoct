package ru.gitverse.adoct.mcp.tools.confluence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.generate.AdocPublisher;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

import java.nio.file.Path;

/**
 * {@code confluence_publish_adoc} — публикует AsciiDoc (файл или папку) в Confluence: рендер в storage
 * format и заливка через REST ({@link AdocPublisher}). ИЗМЕНЯЕТ страницы Confluence.
 */
public final class ConfluencePublishAdoc implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("source", "Абсолютный путь .adoc файла или папки с .adoc", true)
                .str("url", "URL целевой страницы (?pageId=… или /display/SPACE/Title); для папки — родительская", false)
                .str("pageId", "ID целевой страницы (альтернатива url)", false)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_publish_adoc",
                "Опубликовать AsciiDoc (файл или папку) в Confluence: рендер в storage format и заливка "
                        + "через REST. ИЗМЕНЯЕТ страницы Confluence.", schema, args -> {
            String url = c.text(args, "url");
            if (url == null || url.isBlank()) {
                String pageId = c.text(args, "pageId");
                url = pageId == null || pageId.isBlank() ? "" : "pageId=" + pageId;
            }
            String result = new AdocPublisher(c.confluencePublish(args)).publish(url, Path.of(c.reqStr(args, "source")));
            return c.ok(c.mapper().createObjectNode().put("result", result));
        });
    }
}
