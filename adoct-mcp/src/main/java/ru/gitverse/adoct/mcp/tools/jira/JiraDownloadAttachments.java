package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

import java.nio.file.Path;
import java.util.List;

/** {@code jira_download_attachments} — скачивает все вложения задачи Jira в папку. */
public final class JiraDownloadAttachments implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("issueKey", "Ключ задачи", true)
                .str("targetDir", "Абсолютный путь папки назначения", true)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_download_attachments", "Скачать вложения задачи Jira в папку.", schema, args -> {
            List<String> saved = c.jira(args).downloadAttachments(
                    c.reqStr(args, "issueKey"), Path.of(c.reqStr(args, "targetDir")));
            ObjectNode out = c.mapper().createObjectNode();
            out.put("targetDir", c.reqStr(args, "targetDir"));
            ArrayNode files = out.putArray("files");
            saved.forEach(files::add);
            return c.ok(out);
        });
    }
}
