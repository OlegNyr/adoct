package ru.gitverse.adoct.mcp.tools.bitbucket;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/**
 * {@code bitbucket_search} — поиск кода Bitbucket (по содержимому и именам файлов, только дефолтная
 * ветка). Фильтр по проекту/репозиторию через {@code projectKey}/{@code repoSlug}. Потолок выдачи ~1000.
 */
public final class BitbucketSearch implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("query", "Строка поиска (можно с модификаторами Bitbucket: ext:, lang:, path: …)", true)
                .str("projectKey", "Ограничить проектом (ключ)", false)
                .str("repoSlug", "Ограничить репозиторием (slug)", false)
                .integer("limit", "Лимит результатов (1..100, по умолчанию 25)", false)
                .integer("start", "Смещение для пагинации (по умолчанию 0)", false)
                .str("host", "Хост Bitbucket; иначе хост по умолчанию", false)
                .build();
        return new McpTool("bitbucket_search",
                "Поиск кода Bitbucket по содержимому и именам файлов (POST /rest/search/latest/search).",
                schema, args -> c.ok(c.bitbucket(args).searchCode(
                        c.reqStr(args, "query"),
                        c.text(args, "projectKey"),
                        c.text(args, "repoSlug"),
                        c.optInt(args, "start", 0),
                        c.optInt(args, "limit", 25))));
    }
}
