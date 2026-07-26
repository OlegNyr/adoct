package io.github.adoct.mcp.tools.confluence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.adoct.mcp.InputSchema;
import io.github.adoct.mcp.McpTool;
import io.github.adoct.mcp.tools.Tool;
import io.github.adoct.mcp.tools.ToolContext;
import io.github.adoct.parser.DispatcherPage;
import io.github.adoct.parser.OutputFormat;
import io.github.adoct.parser.confluence.ConfluenceClient;
import io.github.adoct.parser.confluence.ObjectMapperExt;

import java.nio.file.Path;

/**
 * {@code confluence_export_tree_to_adoc} — экспортирует Confluence в локальное дерево AsciiDoc/Markdown
 * через {@link DispatcherPage}. По умолчанию — страница и её поддерево; при заданном {@code spaceKey} —
 * всё пространство целиком. Читает Confluence, пишет файлы; сами страницы не меняет.
 */
public final class ConfluenceExportTreeToAdoc implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("pageId", "ID страницы (либо url)", false)
                .str("url", "URL страницы (?pageId=… или /display/SPACE/Title), если нет pageId", false)
                .str("spaceKey", "Ключ пространства — выгрузить пространство целиком (вместо страницы)", false)
                .str("targetDir", "Абсолютный путь папки назначения", true)
                .bool("includeChildren", "Выгружать дерево дочерних (по умолчанию true)", false)
                .bool("includeAttachments", "Скачивать вложения (по умолчанию true)", false)
                .bool("exportColors", "Сохранять оригинальные цвета (по умолчанию false)", false)
                .bool("debug", "Сохранять папку source/ (по умолчанию false)", false)
                .str("format", "Формат вывода: adoc (по умолчанию) или md (Markdown)", false)
                .bool("skipUnchanged", "Пропускать не изменившиеся страницы по версии (по умолчанию true)", false)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_export_tree_to_adoc",
                "Экспортировать Confluence в локальное дерево AsciiDoc/Markdown: страницу с поддеревом или "
                        + "(при spaceKey) всё пространство. Читает Confluence, пишет файлы; страницы не меняет.",
                schema, args -> {
            ConfluenceClient client = c.confluence(args);
            DispatcherPage dp = new DispatcherPage(client, Path.of(c.reqStr(args, "targetDir")),
                    ObjectMapperExt.INSTANT);
            dp.setIncludeChildren(c.optBool(args, "includeChildren", true));
            dp.setIncludeAttachments(c.optBool(args, "includeAttachments", true));
            dp.setExportColors(c.optBool(args, "exportColors", false));
            dp.setDebug(c.optBool(args, "debug", false));
            dp.setFormat(OutputFormat.from(c.text(args, "format")));
            dp.setSkipUnchanged(c.optBool(args, "skipUnchanged", true));
            ObjectNode out = c.mapper().createObjectNode();
            String spaceKey = c.text(args, "spaceKey");
            if (spaceKey != null && !spaceKey.isBlank()) {
                dp.generateSpace(spaceKey.trim(), (t, s) -> { });
                out.put("spaceKey", spaceKey.trim());
            } else {
                String pageId = c.resolvePageId(client, c.text(args, "pageId"), c.text(args, "url"));
                out.put("title", dp.generate(pageId, (t, s) -> { }));
                out.put("pageId", pageId);
            }
            out.put("outputDir", String.valueOf(Path.of(c.reqStr(args, "targetDir"))));
            return c.ok(out);
        });
    }
}
