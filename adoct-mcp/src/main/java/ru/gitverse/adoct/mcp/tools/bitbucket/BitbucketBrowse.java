package ru.gitverse.adoct.mcp.tools.bitbucket;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code bitbucket_browse} — содержимое каталога репозитория (файлы/папки). */
public final class BitbucketBrowse implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("projectKey", "Ключ проекта", true)
                .str("repoSlug", "Slug репозитория", true)
                .str("path", "Путь к каталогу; пусто — корень репозитория", false)
                .str("at", "Ref (ветка/тег/commit); иначе дефолтная ветка", false)
                .integer("limit", "Лимит элементов (по умолчанию 100)", false)
                .integer("start", "Смещение для пагинации (по умолчанию 0)", false)
                .str("host", "Хост Bitbucket; иначе хост по умолчанию", false)
                .build();
        return new McpTool("bitbucket_browse",
                "Содержимое каталога репозитория Bitbucket (файлы и папки).", schema, args ->
                c.ok(c.bitbucket(args).browse(
                        c.reqStr(args, "projectKey"),
                        c.reqStr(args, "repoSlug"),
                        c.text(args, "path"),
                        c.text(args, "at"),
                        c.optInt(args, "start", 0),
                        c.optInt(args, "limit", 100))));
    }
}
