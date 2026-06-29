package ru.gitverse.adoct.mcp.tools.bitbucket;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code bitbucket_get_file} — содержимое файла (строки) через browse. */
public final class BitbucketGetFile implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("projectKey", "Ключ проекта", true)
                .str("repoSlug", "Slug репозитория", true)
                .str("path", "Путь к файлу в репозитории", true)
                .str("at", "Ref (ветка/тег/commit); иначе дефолтная ветка", false)
                .integer("start", "Начальная строка для пагинации (по умолчанию 0)", false)
                .integer("limit", "Сколько строк вернуть (по умолчанию 100)", false)
                .str("host", "Хост Bitbucket; иначе хост по умолчанию", false)
                .build();
        return new McpTool("bitbucket_get_file",
                "Содержимое файла Bitbucket (строки, постранично).", schema, args ->
                c.ok(c.bitbucket(args).browse(
                        c.reqStr(args, "projectKey"),
                        c.reqStr(args, "repoSlug"),
                        c.reqStr(args, "path"),
                        c.text(args, "at"),
                        c.optInt(args, "start", 0),
                        c.optInt(args, "limit", 100))));
    }
}
