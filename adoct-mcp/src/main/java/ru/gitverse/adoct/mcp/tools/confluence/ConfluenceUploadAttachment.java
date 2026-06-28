package ru.gitverse.adoct.mcp.tools.confluence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

import java.nio.file.Path;

/** {@code confluence_upload_attachment} — загружает файл как вложение страницы Confluence. */
public final class ConfluenceUploadAttachment implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("pageId", "ID страницы", true)
                .str("filePath", "Абсолютный путь файла", true)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_upload_attachment",
                "Загрузить файл как вложение страницы Confluence.", schema, args -> {
            String pageId = c.reqStr(args, "pageId");
            c.confluencePublish(args).uploadAttachment(pageId, Path.of(c.reqStr(args, "filePath")));
            return c.ok(c.mapper().createObjectNode().put("uploaded", pageId));
        });
    }
}
