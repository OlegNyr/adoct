package ru.gitverse.adoct.mcp.tools.confluence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

import java.nio.file.Path;

/** {@code confluence_download_attachment} — скачивает вложение страницы Confluence в папку. */
public final class ConfluenceDownloadAttachment implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("pageId", "ID страницы", true)
                .str("fileName", "Имя вложения", true)
                .str("targetDir", "Абсолютный путь папки назначения", true)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_download_attachment",
                "Скачать вложение страницы Confluence в папку.", schema, args -> {
            String saved = c.confluencePublish(args).downloadAttachment(
                    c.reqStr(args, "pageId"), c.reqStr(args, "fileName"), Path.of(c.reqStr(args, "targetDir")));
            ObjectNode out = c.mapper().createObjectNode();
            out.put("file", saved);
            out.put("targetDir", c.reqStr(args, "targetDir"));
            return c.ok(out);
        });
    }
}
