package ru.gitverse.adoct.mcp.tools.bitbucket;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code bitbucket_get_pull_request_activities} — лента активности PR (комментарии + события). */
public final class BitbucketGetPullRequestActivities implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("projectKey", "Ключ проекта", true)
                .str("repoSlug", "Slug репозитория", true)
                .integer("id", "Номер pull request'а", true)
                .integer("limit", "Лимит (1..100, по умолчанию 25)", false)
                .integer("start", "Смещение для пагинации (по умолчанию 0)", false)
                .str("host", "Хост Bitbucket; иначе хост по умолчанию", false)
                .build();
        return new McpTool("bitbucket_get_pull_request_activities",
                "Активность pull request'а Bitbucket: комментарии и события.", schema, args ->
                c.ok(c.bitbucket(args).getPullRequestActivities(
                        c.reqStr(args, "projectKey"), c.reqStr(args, "repoSlug"),
                        c.reqInt(args, "id"), c.optInt(args, "start", 0), c.optInt(args, "limit", 25))));
    }
}
