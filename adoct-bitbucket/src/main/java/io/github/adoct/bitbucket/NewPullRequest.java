package io.github.adoct.bitbucket;

import lombok.Builder;

import java.util.List;

/**
 * Параметры нового pull request'а для {@link BitbucketClient#createPullRequest(NewPullRequest)}.
 *
 * <p>Ветки можно задавать коротким именем ({@code feature/x}) или полным ref ({@code refs/heads/feature/x}).
 * Пустой {@code toBranch} означает дефолтную ветку целевого репозитория. {@code fromProjectKey}/
 * {@code fromRepoSlug} задаются только для PR из форка — иначе источник совпадает с целевым репозиторием.
 *
 * @param projectKey     ключ проекта целевого репозитория
 * @param repoSlug       slug целевого репозитория
 * @param title          заголовок PR
 * @param description    описание PR (может быть пустым)
 * @param fromBranch     исходная ветка
 * @param toBranch       целевая ветка; пусто — дефолтная ветка репозитория
 * @param fromProjectKey ключ проекта форка-источника; пусто — {@code projectKey}
 * @param fromRepoSlug   slug форка-источника; пусто — {@code repoSlug}
 * @param reviewers      логины ревьюеров
 * @param draft          создать черновик (Bitbucket 8.18+)
 */
@Builder
public record NewPullRequest(
        String projectKey,
        String repoSlug,
        String title,
        String description,
        String fromBranch,
        String toBranch,
        String fromProjectKey,
        String fromRepoSlug,
        List<String> reviewers,
        boolean draft) {

    public NewPullRequest {
        requireText(projectKey, "projectKey");
        requireText(repoSlug, "repoSlug");
        requireText(title, "title");
        requireText(fromBranch, "fromBranch");
        reviewers = reviewers == null
                ? List.of()
                : reviewers.stream().filter(r -> r != null && !r.isBlank()).map(String::trim).toList();
    }

    /** Проект репозитория-источника: форк, если задан, иначе целевой. */
    public String sourceProjectKey() {
        return isBlank(fromProjectKey) ? projectKey : fromProjectKey.trim();
    }

    /** Slug репозитория-источника: форк, если задан, иначе целевой. */
    public String sourceRepoSlug() {
        return isBlank(fromRepoSlug) ? repoSlug : fromRepoSlug.trim();
    }

    private static void requireText(String value, String name) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("Не задан обязательный параметр pull request'а: " + name);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
