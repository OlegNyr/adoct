package ru.gitverse.adoct.mcp.tools;

import ru.gitverse.adoct.mcp.EndpointSupplier;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.confluence.ConfluenceAddComment;
import ru.gitverse.adoct.mcp.tools.confluence.ConfluenceAddLabels;
import ru.gitverse.adoct.mcp.tools.confluence.ConfluenceDeleteAttachment;
import ru.gitverse.adoct.mcp.tools.confluence.ConfluenceDeleteLabel;
import ru.gitverse.adoct.mcp.tools.confluence.ConfluenceDeletePage;
import ru.gitverse.adoct.mcp.tools.confluence.ConfluenceDownloadAttachment;
import ru.gitverse.adoct.mcp.tools.confluence.ConfluenceExportTreeToAdoc;
import ru.gitverse.adoct.mcp.tools.confluence.ConfluenceFindPage;
import ru.gitverse.adoct.mcp.tools.confluence.ConfluenceGetAttachments;
import ru.gitverse.adoct.mcp.tools.confluence.ConfluenceGetChildPages;
import ru.gitverse.adoct.mcp.tools.confluence.ConfluenceGetComments;
import ru.gitverse.adoct.mcp.tools.confluence.ConfluenceGetLabels;
import ru.gitverse.adoct.mcp.tools.confluence.ConfluenceGetPage;
import ru.gitverse.adoct.mcp.tools.confluence.ConfluenceGetPageDiff;
import ru.gitverse.adoct.mcp.tools.confluence.ConfluenceGetPageHistory;
import ru.gitverse.adoct.mcp.tools.confluence.ConfluenceGetUser;
import ru.gitverse.adoct.mcp.tools.confluence.ConfluenceMovePage;
import ru.gitverse.adoct.mcp.tools.confluence.ConfluencePublishAdoc;
import ru.gitverse.adoct.mcp.tools.confluence.ConfluenceReplyToComment;
import ru.gitverse.adoct.mcp.tools.confluence.ConfluenceSearch;
import ru.gitverse.adoct.mcp.tools.confluence.ConfluenceUploadAttachment;
import ru.gitverse.adoct.mcp.tools.jira.JiraAddComment;
import ru.gitverse.adoct.mcp.tools.jira.JiraAddIssuesToSprint;
import ru.gitverse.adoct.mcp.tools.jira.JiraAddWatcher;
import ru.gitverse.adoct.mcp.tools.jira.JiraAddWorklog;
import ru.gitverse.adoct.mcp.tools.jira.JiraBatchCreateIssues;
import ru.gitverse.adoct.mcp.tools.jira.JiraCreateIssue;
import ru.gitverse.adoct.mcp.tools.jira.JiraCreateIssueLink;
import ru.gitverse.adoct.mcp.tools.jira.JiraCreateRemoteIssueLink;
import ru.gitverse.adoct.mcp.tools.jira.JiraCreateSprint;
import ru.gitverse.adoct.mcp.tools.jira.JiraCreateVersion;
import ru.gitverse.adoct.mcp.tools.jira.JiraDeleteIssue;
import ru.gitverse.adoct.mcp.tools.jira.JiraDownloadAttachments;
import ru.gitverse.adoct.mcp.tools.jira.JiraGetAttachments;
import ru.gitverse.adoct.mcp.tools.jira.JiraGetBoardBacklog;
import ru.gitverse.adoct.mcp.tools.jira.JiraGetChangelog;
import ru.gitverse.adoct.mcp.tools.jira.JiraGetCurrentUser;
import ru.gitverse.adoct.mcp.tools.jira.JiraGetIssue;
import ru.gitverse.adoct.mcp.tools.jira.JiraGetLinkTypes;
import ru.gitverse.adoct.mcp.tools.jira.JiraGetProjectComponents;
import ru.gitverse.adoct.mcp.tools.jira.JiraGetProjectVersions;
import ru.gitverse.adoct.mcp.tools.jira.JiraGetSprintIssues;
import ru.gitverse.adoct.mcp.tools.jira.JiraGetTransitions;
import ru.gitverse.adoct.mcp.tools.jira.JiraGetWatchers;
import ru.gitverse.adoct.mcp.tools.jira.JiraGetWorklog;
import ru.gitverse.adoct.mcp.tools.jira.JiraLinkToEpic;
import ru.gitverse.adoct.mcp.tools.jira.JiraListBoards;
import ru.gitverse.adoct.mcp.tools.jira.JiraListProjects;
import ru.gitverse.adoct.mcp.tools.jira.JiraListSprints;
import ru.gitverse.adoct.mcp.tools.jira.JiraRemoveIssueLink;
import ru.gitverse.adoct.mcp.tools.jira.JiraRemoveWatcher;
import ru.gitverse.adoct.mcp.tools.jira.JiraSearch;
import ru.gitverse.adoct.mcp.tools.jira.JiraSearchFields;
import ru.gitverse.adoct.mcp.tools.jira.JiraTransitionIssue;
import ru.gitverse.adoct.mcp.tools.jira.JiraUpdateIssue;
import ru.gitverse.adoct.mcp.tools.jira.JiraUpdateSprint;

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

    /** Фабрики всех инструментов (по одному классу на инструмент). */
    private static List<Tool> factories() {
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
                // ---- Confluence ----
                new ConfluenceGetPage(),
                new ConfluenceSearch(),
                new ConfluenceFindPage(),
                new ConfluenceGetChildPages(),
                new ConfluenceGetUser(),
                new ConfluenceExportTreeToAdoc(),
                new ConfluencePublishAdoc(),
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
                new ConfluenceDeleteAttachment());
    }

    /** Строит список всех инструментов с общим контекстом. */
    public List<McpTool> tools() {
        return factories().stream().map(t -> t.create(ctx)).toList();
    }
}
