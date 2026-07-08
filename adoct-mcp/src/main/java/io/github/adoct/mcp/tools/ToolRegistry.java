package io.github.adoct.mcp.tools;

import io.github.adoct.mcp.AtlassianKind;
import io.github.adoct.mcp.EndpointSupplier;
import io.github.adoct.mcp.McpTool;
import io.github.adoct.mcp.tools.confluence.ConfluenceAddComment;
import io.github.adoct.mcp.tools.confluence.ConfluenceAddLabels;
import io.github.adoct.mcp.tools.confluence.ConfluenceDeleteAttachment;
import io.github.adoct.mcp.tools.confluence.ConfluenceDeleteLabel;
import io.github.adoct.mcp.tools.confluence.ConfluenceDeletePage;
import io.github.adoct.mcp.tools.confluence.ConfluenceDownloadAttachment;
import io.github.adoct.mcp.tools.confluence.ConfluenceExportTreeToAdoc;
import io.github.adoct.mcp.tools.confluence.ConfluenceFindPage;
import io.github.adoct.mcp.tools.confluence.ConfluenceGetAttachments;
import io.github.adoct.mcp.tools.confluence.ConfluenceGetChildPages;
import io.github.adoct.mcp.tools.confluence.ConfluenceGetDefaultSpace;
import io.github.adoct.mcp.tools.confluence.ConfluenceGetComments;
import io.github.adoct.mcp.tools.confluence.ConfluenceGetLabels;
import io.github.adoct.mcp.tools.confluence.ConfluenceGetPage;
import io.github.adoct.mcp.tools.confluence.ConfluenceGetPageDiff;
import io.github.adoct.mcp.tools.confluence.ConfluenceGetPageHistory;
import io.github.adoct.mcp.tools.confluence.ConfluenceGetUser;
import io.github.adoct.mcp.tools.confluence.ConfluenceListSpaces;
import io.github.adoct.mcp.tools.confluence.ConfluenceMovePage;
import io.github.adoct.mcp.tools.confluence.ConfluencePublishAdoc;
import io.github.adoct.mcp.tools.confluence.ConfluenceReplyToComment;
import io.github.adoct.mcp.tools.confluence.ConfluenceSearch;
import io.github.adoct.mcp.tools.confluence.ConfluenceUploadAttachment;
import io.github.adoct.mcp.tools.bitbucket.BitbucketBrowse;
import io.github.adoct.mcp.tools.bitbucket.BitbucketGetFile;
import io.github.adoct.mcp.tools.bitbucket.BitbucketGetPullRequest;
import io.github.adoct.mcp.tools.bitbucket.BitbucketGetPullRequestActivities;
import io.github.adoct.mcp.tools.bitbucket.BitbucketGetPullRequestDiff;
import io.github.adoct.mcp.tools.bitbucket.BitbucketGetRepository;
import io.github.adoct.mcp.tools.bitbucket.BitbucketListProjects;
import io.github.adoct.mcp.tools.bitbucket.BitbucketListPullRequests;
import io.github.adoct.mcp.tools.bitbucket.BitbucketListRepositories;
import io.github.adoct.mcp.tools.bitbucket.BitbucketSearch;
import io.github.adoct.mcp.tools.jira.JiraAddComment;
import io.github.adoct.mcp.tools.jira.JiraAssignIssue;
import io.github.adoct.mcp.tools.jira.JiraAddIssuesToSprint;
import io.github.adoct.mcp.tools.jira.JiraAddWatcher;
import io.github.adoct.mcp.tools.jira.JiraAddWorklog;
import io.github.adoct.mcp.tools.jira.JiraBatchCreateIssues;
import io.github.adoct.mcp.tools.jira.JiraCreateIssue;
import io.github.adoct.mcp.tools.jira.JiraCreateIssueLink;
import io.github.adoct.mcp.tools.jira.JiraCreateRemoteIssueLink;
import io.github.adoct.mcp.tools.jira.JiraCreateSprint;
import io.github.adoct.mcp.tools.jira.JiraCreateVersion;
import io.github.adoct.mcp.tools.jira.JiraDeleteIssue;
import io.github.adoct.mcp.tools.jira.JiraDownloadAttachments;
import io.github.adoct.mcp.tools.jira.JiraGetAttachments;
import io.github.adoct.mcp.tools.jira.JiraGetBoardBacklog;
import io.github.adoct.mcp.tools.jira.JiraGetChangelog;
import io.github.adoct.mcp.tools.jira.JiraGetCurrentUser;
import io.github.adoct.mcp.tools.jira.JiraGetIssue;
import io.github.adoct.mcp.tools.jira.JiraGetLinkTypes;
import io.github.adoct.mcp.tools.jira.JiraGetProjectComponents;
import io.github.adoct.mcp.tools.jira.JiraGetProjectStatuses;
import io.github.adoct.mcp.tools.jira.JiraGetProjectVersions;
import io.github.adoct.mcp.tools.jira.JiraGetWorkflow;
import io.github.adoct.mcp.tools.jira.JiraGetSprintIssues;
import io.github.adoct.mcp.tools.jira.JiraGetTransitions;
import io.github.adoct.mcp.tools.jira.JiraGetWatchers;
import io.github.adoct.mcp.tools.jira.JiraGetWorklog;
import io.github.adoct.mcp.tools.jira.JiraLinkIssues;
import io.github.adoct.mcp.tools.jira.JiraLinkToEpic;
import io.github.adoct.mcp.tools.jira.JiraListAssignableUsers;
import io.github.adoct.mcp.tools.jira.JiraListBoards;
import io.github.adoct.mcp.tools.jira.JiraListProjects;
import io.github.adoct.mcp.tools.jira.JiraListSprints;
import io.github.adoct.mcp.tools.jira.JiraListTeam;
import io.github.adoct.mcp.tools.jira.JiraListTemplates;
import io.github.adoct.mcp.tools.jira.JiraRemoveIssueLink;
import io.github.adoct.mcp.tools.jira.JiraRemoveWatcher;
import io.github.adoct.mcp.tools.jira.JiraSearch;
import io.github.adoct.mcp.tools.jira.JiraSearchFields;
import io.github.adoct.mcp.tools.jira.JiraTransitionIssue;
import io.github.adoct.mcp.tools.jira.JiraUpdateIssue;
import io.github.adoct.mcp.tools.jira.JiraUpdateSprint;

import java.util.ArrayList;
import java.util.List;

/**
 * Реестр-диспетчер инструментов MCP. Перечисляет все {@link Tool}-реализации из пакетов
 * {@code tools.jira} и {@code tools.confluence} и собирает из них список {@link McpTool},
 * передавая каждому общий {@link ToolContext}. Добавление инструмента = новый класс + строка здесь.
 */
public final class ToolRegistry {

    private final ToolContext ctx;

    public ToolRegistry(EndpointSupplier endpoints) {
        this.ctx = new ToolContext(endpoints);
    }

    /**
     * Фабрики инструментов без asciidoctorj-зависимого {@code confluence_publish_adoc} (тот тянет
     * JRuby и несовместим с GraalVM native-image). Native-сборка использует {@link #coreTools()},
     * который ссылается только сюда — поэтому {@code ConfluencePublishAdoc} в native-граф не попадает.
     */
    private static List<Tool> coreFactories() {
        return List.of(
                // ---- Jira ----
                new JiraGetIssue(),
                new JiraSearch(),
                new JiraGetTransitions(),
                new JiraCreateIssue(),
                new JiraUpdateIssue(),
                new JiraTransitionIssue(),
                new JiraAddComment(),
                new JiraListProjects(),
                new JiraGetCurrentUser(),
                new JiraListBoards(),
                new JiraListSprints(),
                new JiraGetSprintIssues(),
                new JiraGetBoardBacklog(),
                new JiraDeleteIssue(),
                new JiraBatchCreateIssues(),
                new JiraGetWorklog(),
                new JiraAddWorklog(),
                new JiraGetLinkTypes(),
                new JiraCreateIssueLink(),
                new JiraLinkIssues(),
                new JiraRemoveIssueLink(),
                new JiraCreateRemoteIssueLink(),
                new JiraLinkToEpic(),
                new JiraGetWatchers(),
                new JiraAddWatcher(),
                new JiraRemoveWatcher(),
                new JiraGetProjectVersions(),
                new JiraCreateVersion(),
                new JiraGetProjectComponents(),
                new JiraCreateSprint(),
                new JiraUpdateSprint(),
                new JiraAddIssuesToSprint(),
                new JiraSearchFields(),
                new JiraGetChangelog(),
                new JiraGetAttachments(),
                new JiraDownloadAttachments(),
                // ---- Jira: команда / шаблоны / workflow ----
                new JiraListTeam(),
                new JiraListAssignableUsers(),
                new JiraListTemplates(),
                new JiraGetWorkflow(),
                new JiraGetProjectStatuses(),
                new JiraAssignIssue(),
                // ---- Confluence ----
                new ConfluenceGetPage(),
                new ConfluenceSearch(),
                new ConfluenceListSpaces(),
                new ConfluenceGetDefaultSpace(),
                new ConfluenceFindPage(),
                new ConfluenceGetChildPages(),
                new ConfluenceGetUser(),
                new ConfluenceExportTreeToAdoc(),
                new ConfluenceDeletePage(),
                new ConfluenceMovePage(),
                new ConfluenceGetPageHistory(),
                new ConfluenceGetPageDiff(),
                new ConfluenceGetComments(),
                new ConfluenceAddComment(),
                new ConfluenceReplyToComment(),
                new ConfluenceGetLabels(),
                new ConfluenceAddLabels(),
                new ConfluenceDeleteLabel(),
                new ConfluenceGetAttachments(),
                new ConfluenceUploadAttachment(),
                new ConfluenceDownloadAttachment(),
                new ConfluenceDeleteAttachment(),
                // ---- Bitbucket (Server/DC) ----
                new BitbucketSearch(),
                new BitbucketListProjects(),
                new BitbucketListRepositories(),
                new BitbucketGetRepository(),
                new BitbucketGetFile(),
                new BitbucketBrowse(),
                new BitbucketListPullRequests(),
                new BitbucketGetPullRequest(),
                new BitbucketGetPullRequestDiff(),
                new BitbucketGetPullRequestActivities());
    }

    /** Полный набор фабрик: core + asciidoctorj-зависимый {@code confluence_publish_adoc}. */
    private static List<Tool> allFactories() {
        List<Tool> all = new ArrayList<>(coreFactories());
        all.add(new ConfluencePublishAdoc());
        return all;
    }

    /** Полный набор инструментов (включая publish_adoc) — для JVM/плагина. */
    public List<McpTool> tools() {
        return build(allFactories());
    }

    /** Набор без asciidoctorj/JRuby (native-совместимый) — для GraalVM native-сборки CLI. */
    public List<McpTool> coreTools() {
        return build(coreFactories());
    }

    private List<McpTool> build(List<Tool> factories) {
        java.util.Set<AtlassianKind> enabled = ctx.enabledToolGroups();
        return factories.stream()
                .map(t -> t.create(ctx))
                .filter(tool -> groupEnabled(tool.name(), enabled))
                .toList();
    }

    /**
     * Включена ли группа тула: по префиксу имени ({@code jira_}/{@code confluence_}/{@code bitbucket_}).
     * Тулы без распознанного префикса показываются всегда.
     */
    private static boolean groupEnabled(String name, java.util.Set<AtlassianKind> enabled) {
        AtlassianKind kind = name.startsWith("jira_") ? AtlassianKind.JIRA
                : name.startsWith("confluence_") ? AtlassianKind.CONFLUENCE
                : name.startsWith("bitbucket_") ? AtlassianKind.BITBUCKET
                : null;
        return kind == null || enabled.contains(kind);
    }
}
