package io.github.adoct.mcp.tools.bitbucket;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.adoct.mcp.InputSchema;
import io.github.adoct.mcp.McpTool;
import io.github.adoct.mcp.tools.Tool;
import io.github.adoct.mcp.tools.ToolContext;

/** {@code bitbucket_get_repository} — карточка репозитория. */
public final class BitbucketGetRepository implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("projectKey", "Ключ проекта", true)
                .str("repoSlug", "Slug репозитория", true)
                .str("host", "Хост Bitbucket; иначе хост по умолчанию", false)
                .build();
        return new McpTool("bitbucket_get_repository", "Информация о репозитории Bitbucket.", schema, args ->
                c.ok(c.bitbucket(args).getRepository(c.reqStr(args, "projectKey"), c.reqStr(args, "repoSlug"))));
    }
}
