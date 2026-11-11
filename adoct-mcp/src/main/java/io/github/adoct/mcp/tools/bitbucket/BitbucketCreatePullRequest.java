package io.github.adoct.mcp.tools.bitbucket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.adoct.bitbucket.NewPullRequest;
import io.github.adoct.mcp.InputSchema;
import io.github.adoct.mcp.McpTool;
import io.github.adoct.mcp.tools.Tool;
import io.github.adoct.mcp.tools.ToolContext;

import java.util.List;

/**
 * {@code bitbucket_create_pull_request} — создаёт pull request. Целевая ветка по умолчанию — дефолтная ветка
 * репозитория; для PR из форка задаются {@code fromProjectKey}/{@code fromRepoSlug}.
 */
public final class BitbucketCreatePullRequest implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("projectKey", "Ключ проекта целевого репозитория", true)
                .str("repoSlug", "Slug целевого репозитория", true)
                .str("title", "Заголовок pull request'а", true)
                .str("fromBranch", "Исходная ветка (имя или refs/heads/…)", true)
                .str("toBranch", "Целевая ветка; иначе дефолтная ветка репозитория", false)
                .str("description", "Описание (Markdown), необязательно", false)
                .arr("reviewers", "Логины ревьюеров (массив строк)", false)
                .str("fromProjectKey", "Проект форка-источника (только для PR из форка, напр. ~LOGIN)", false)
                .str("fromRepoSlug", "Slug форка-источника (только для PR из форка)", false)
                .bool("draft", "Создать черновик (Bitbucket 8.18+), по умолчанию false", false)
                .str("host", "Хост Bitbucket; иначе хост по умолчанию", false)
                .build();
        return new McpTool("bitbucket_create_pull_request",
                "Создать pull request в Bitbucket (ветка → ветка, ревьюеры, черновик, PR из форка).", schema, args -> {
            NewPullRequest pr = NewPullRequest.builder()
                    .projectKey(c.reqStr(args, "projectKey"))
                    .repoSlug(c.reqStr(args, "repoSlug"))
                    .title(c.reqStr(args, "title"))
                    .description(c.text(args, "description"))
                    .fromBranch(c.reqStr(args, "fromBranch"))
                    .toBranch(c.text(args, "toBranch"))
                    .fromProjectKey(c.text(args, "fromProjectKey"))
                    .fromRepoSlug(c.text(args, "fromRepoSlug"))
                    .reviewers(reviewers(c, args))
                    .draft(c.optBool(args, "draft", false))
                    .build();
            return c.ok(c.bitbucket(args).createPullRequest(pr));
        });
    }

    private static List<String> reviewers(ToolContext c, JsonNode args) {
        JsonNode node = args == null ? null : args.get("reviewers");
        return node == null || node.isNull() ? List.of() : c.strList(args, "reviewers");
    }
}
