package ru.gitverse.adoct.mcp.tools.bitbucket;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code bitbucket_get_pull_request} — карточка pull request'а. */
public final class BitbucketGetPullRequest implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("projectKey", "Ключ проекта", true)
                .str("repoSlug", "Slug репозитория", true)
                .integer("id", "Номер pull request'а", true)
                .str("host", "Хост Bitbucket; иначе хост по умолчанию", false)
                .build();
        return new McpTool("bitbucket_get_pull_request", "Информация о pull request'е Bitbucket.", schema, args ->
                c.ok(c.bitbucket(args).getPullRequest(
                        c.reqStr(args, "projectKey"), c.reqStr(args, "repoSlug"), c.reqInt(args, "id"))));
    }
}
