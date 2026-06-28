package ru.gitverse.adoct.mcp.tools.confluence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;
import ru.gitverse.adoct.parser.DispatcherPage;
import ru.gitverse.adoct.parser.confluence.ConfluenceClient;
import ru.gitverse.adoct.parser.confluence.ObjectMapperExt;

import java.nio.file.Path;

/**
 * {@code confluence_export_tree_to_adoc} — экспортирует страницу Confluence и её поддерево в локальное
 * дерево AsciiDoc через {@link DispatcherPage}. Читает Confluence, пишет файлы; саму страницу не меняет.
 */
public final class ConfluenceExportTreeToAdoc implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("pageId", "ID страницы (либо url)", false)
                .str("url", "URL страницы (?pageId=… или /display/SPACE/Title), если нет pageId", false)
                .str("targetDir", "Абсолютный путь папки назначения", true)
                .bool("includeChildren", "Выгружать дерево дочерних (по умолчанию true)", false)
                .bool("includeAttachments", "Скачивать вложения (по умолчанию true)", false)
                .bool("exportColors", "Сохранять оригинальные цвета (по умолчанию false)", false)
                .bool("debug", "Сохранять папку source/ (по умолчанию false)", false)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_export_tree_to_adoc",
                "Экспортировать страницу Confluence и её поддерево в локальное дерево AsciiDoc "
                        + "(читает Confluence, пишет файлы; саму страницу не меняет).", schema, args -> {
            ConfluenceClient client = c.confluence(args);
            String pageId = c.resolvePageId(client, c.text(args, "pageId"), c.text(args, "url"));
            DispatcherPage dp = new DispatcherPage(client, Path.of(c.reqStr(args, "targetDir")),
                    ObjectMapperExt.INSTANT);
            dp.setIncludeChildren(c.optBool(args, "includeChildren", true));
            dp.setIncludeAttachments(c.optBool(args, "includeAttachments", true));
            dp.setExportColors(c.optBool(args, "exportColors", false));
            dp.setDebug(c.optBool(args, "debug", false));
            String title = dp.generate(pageId, (t, s) -> { });
            ObjectNode out = c.mapper().createObjectNode();
            out.put("title", title);
            out.put("pageId", pageId);
            out.put("outputDir", String.valueOf(dp.getDestination()));
            return c.ok(out);
        });
    }
}
