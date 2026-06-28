package ru.gitverse.adoct.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.jira.JiraClient;
import ru.gitverse.adoct.mcp.AtlassianEndpoint;
import ru.gitverse.adoct.mcp.EndpointSupplier;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.ToolResult;
import ru.gitverse.adoct.generate.AdocPublisher;
import ru.gitverse.adoct.parser.DispatcherPage;
import ru.gitverse.adoct.parser.confluence.ConfluenceClient;
import ru.gitverse.adoct.parser.confluence.ContentPage;
import ru.gitverse.adoct.parser.confluence.LinkResult;
import ru.gitverse.adoct.parser.confluence.ObjectMapperExt;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Каталог read-only тулов MCP (этап 1): чтение Jira и Confluence + экспорт дерева страниц в AsciiDoc.
 * Каждый тул резолвит доступ через {@link EndpointSupplier} (необязательный аргумент {@code host},
 * иначе точка по умолчанию) и сериализует результат в JSON-текст.
 */
public final class ToolCatalog {

    private static final Pattern PAGE_ID = Pattern.compile("pageId=(\\d+)");
    private static final Pattern DISPLAY = Pattern.compile("/display/([^/?#]+)/([^/?#]+)");

    private final EndpointSupplier endpoints;
    private final ObjectMapper mapper = ObjectMapperExt.INSTANT;

    public ToolCatalog(EndpointSupplier endpoints) {
        this.endpoints = endpoints;
    }

    public List<McpTool> tools() {
        return List.of(
                jiraGetIssue(),
                jiraSearch(),
                jiraGetTransitions(),
                jiraCreateIssue(),
                jiraUpdateIssue(),
                jiraTransitionIssue(),
                jiraAddComment(),
                jiraListProjects(),
                jiraGetCurrentUser(),
                jiraListBoards(),
                jiraListSprints(),
                jiraGetSprintIssues(),
                jiraGetBoardBacklog(),
                confluenceGetPage(),
                confluenceSearch(),
                confluenceFindPage(),
                confluenceGetChildPages(),
                confluenceGetUser(),
                confluenceExportTreeToAdoc(),
                confluencePublishAdoc());
    }

    // ---- Jira -------------------------------------------------------------

    private McpTool jiraGetIssue() {
        ObjectNode schema = InputSchema.object()
                .str("issueKey", "Ключ задачи, например ABC-123", true)
                .str("fields", "Поля через запятую или *all (по умолчанию базовый набор)", false)
                .str("host", "Хост Jira (если настроено несколько); иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_get_issue", "Прочитать задачу Jira по ключу.", schema, args -> {
            JsonNode issue = jira(args).getIssue(reqStr(args, "issueKey"), text(args, "fields"));
            return ToolResult.ok(mapper.writeValueAsString(issue));
        });
    }

    private McpTool jiraSearch() {
        ObjectNode schema = InputSchema.object()
                .str("jql", "JQL-запрос", true)
                .integer("maxResults", "Лимит результатов (1..100, по умолчанию 50)", false)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_search", "Найти задачи Jira по JQL.", schema, args -> {
            JsonNode result = jira(args).searchJql(reqStr(args, "jql"), optInt(args, "maxResults", 50));
            return ToolResult.ok(mapper.writeValueAsString(result));
        });
    }

    private McpTool jiraGetTransitions() {
        ObjectNode schema = InputSchema.object()
                .str("issueKey", "Ключ задачи", true)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_get_transitions",
                "Доступные переходы по workflow для задачи (id и название).", schema, args -> {
            JsonNode t = jira(args).getTransitions(reqStr(args, "issueKey"));
            return ToolResult.ok(mapper.writeValueAsString(t));
        });
    }

    private McpTool jiraCreateIssue() {
        ObjectNode schema = InputSchema.object()
                .str("projectKey", "Ключ проекта (по умолчанию из настроек)", false)
                .str("issueType", "Тип задачи (например Task, Story, Bug)", true)
                .str("summary", "Заголовок", true)
                .str("description", "Описание (необязательно)", false)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_create_issue", "Создать задачу Jira.", schema, args -> {
            ObjectNode payload = mapper.createObjectNode();
            ObjectNode fields = payload.putObject("fields");
            fields.putObject("project").put("key", requireProject(args));
            fields.putObject("issuetype").put("name", reqStr(args, "issueType"));
            fields.put("summary", reqStr(args, "summary"));
            String description = text(args, "description");
            if (description != null && !description.isBlank()) {
                fields.put("description", description);
            }
            String key = jira(args).createIssue(payload);
            ObjectNode out = mapper.createObjectNode();
            out.put("key", key);
            return ToolResult.ok(mapper.writeValueAsString(out));
        });
    }

    private McpTool jiraUpdateIssue() {
        ObjectNode schema = InputSchema.object()
                .str("issueKey", "Ключ задачи", true)
                .obj("fields", "Объект полей Jira для обновления (например {\"summary\":\"…\"})", true)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_update_issue", "Обновить поля задачи Jira.", schema, args -> {
            JsonNode fields = args.get("fields");
            if (fields == null || !fields.isObject()) {
                throw new IllegalArgumentException("Параметр fields должен быть объектом");
            }
            ObjectNode payload = mapper.createObjectNode();
            payload.set("fields", fields);
            String issueKey = reqStr(args, "issueKey");
            jira(args).updateIssue(issueKey, payload);
            ObjectNode out = mapper.createObjectNode();
            out.put("updated", issueKey);
            return ToolResult.ok(mapper.writeValueAsString(out));
        });
    }

    private McpTool jiraTransitionIssue() {
        ObjectNode schema = InputSchema.object()
                .str("issueKey", "Ключ задачи", true)
                .str("transitionId", "ID перехода (см. jira_get_transitions)", true)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_transition_issue",
                "Перевести задачу Jira по переходу workflow.", schema, args -> {
            String issueKey = reqStr(args, "issueKey");
            String transitionId = reqStr(args, "transitionId");
            jira(args).transitionIssue(issueKey, transitionId);
            ObjectNode out = mapper.createObjectNode();
            out.put("transitioned", issueKey);
            out.put("transitionId", transitionId);
            return ToolResult.ok(mapper.writeValueAsString(out));
        });
    }

    private McpTool jiraAddComment() {
        ObjectNode schema = InputSchema.object()
                .str("issueKey", "Ключ задачи", true)
                .str("body", "Текст комментария", true)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_add_comment", "Добавить комментарий к задаче Jira.", schema, args -> {
            JsonNode comment = jira(args).addComment(reqStr(args, "issueKey"), reqStr(args, "body"));
            return ToolResult.ok(mapper.writeValueAsString(comment));
        });
    }

    private McpTool jiraListProjects() {
        ObjectNode schema = InputSchema.object()
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_list_projects", "Список проектов Jira.", schema, args ->
                ToolResult.ok(mapper.writeValueAsString(jira(args).listProjects())));
    }

    private McpTool jiraGetCurrentUser() {
        ObjectNode schema = InputSchema.object()
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_get_current_user", "Текущий пользователь Jira (владелец токена).", schema, args ->
                ToolResult.ok(mapper.writeValueAsString(jira(args).getCurrentUser())));
    }

    private McpTool jiraListBoards() {
        ObjectNode schema = InputSchema.object()
                .str("projectKeyOrId", "Фильтр по проекту (по умолчанию из настроек)", false)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_list_boards", "Agile-доски Jira (опц. фильтр по проекту).", schema, args ->
                ToolResult.ok(mapper.writeValueAsString(jira(args).listBoards(
                        firstNonBlank(text(args, "projectKeyOrId"), endpoints.defaultJiraProject().orElse(null))))));
    }

    private McpTool jiraListSprints() {
        ObjectNode schema = InputSchema.object()
                .str("boardId", "ID доски", true)
                .str("state", "Фильтр состояний через запятую: active,future,closed (необязательно)", false)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_list_sprints", "Спринты доски Jira.", schema, args ->
                ToolResult.ok(mapper.writeValueAsString(
                        jira(args).listSprints(reqStr(args, "boardId"), text(args, "state")))));
    }

    private McpTool jiraGetSprintIssues() {
        ObjectNode schema = InputSchema.object()
                .str("sprintId", "ID спринта", true)
                .integer("maxResults", "Лимит (1..100, по умолчанию 50)", false)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_get_sprint_issues", "Задачи спринта Jira.", schema, args ->
                ToolResult.ok(mapper.writeValueAsString(
                        jira(args).getSprintIssues(reqStr(args, "sprintId"), optInt(args, "maxResults", 50)))));
    }

    private McpTool jiraGetBoardBacklog() {
        ObjectNode schema = InputSchema.object()
                .str("boardId", "ID доски", true)
                .integer("maxResults", "Лимит (1..100, по умолчанию 50)", false)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_get_board_backlog", "Бэклог доски Jira.", schema, args ->
                ToolResult.ok(mapper.writeValueAsString(
                        jira(args).getBoardBacklog(reqStr(args, "boardId"), optInt(args, "maxResults", 50)))));
    }

    // ---- Confluence (read) ------------------------------------------------

    private McpTool confluenceGetPage() {
        ObjectNode schema = InputSchema.object()
                .str("pageId", "ID страницы Confluence", true)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_get_page",
                "Прочитать страницу Confluence (storage-тело и вложения) по ID.", schema, args -> {
            ConfluenceClient client = confluence(args);
            String pageId = reqStr(args, "pageId");
            ContentPage cp = client.getMainPage(pageId);
            ObjectNode out = mapper.createObjectNode();
            out.put("pageId", pageId);
            out.put("title", cp.title());
            out.put("url", cp.url());
            out.put("date", cp.date());
            out.put("storage", cp.content());
            ArrayNode atts = out.putArray("attachments");
            cp.attachment().keySet().forEach(atts::add);
            return ToolResult.ok(mapper.writeValueAsString(out));
        });
    }

    private McpTool confluenceSearch() {
        ObjectNode schema = InputSchema.object()
                .str("query", "Заголовок страницы для поиска (CQL: точное, затем нечёткое)", true)
                .str("spaceKey", "Ограничить пространством (по умолчанию из настроек)", false)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_search",
                "Найти страницы Confluence по заголовку (CQL).", schema, args -> {
            ConfluenceClient client = confluence(args);
            List<LinkResult> results = client.search(reqStr(args, "query"),
                    firstNonBlank(text(args, "spaceKey"), endpoints.defaultConfluenceSpace().orElse(null)));
            return ToolResult.ok(mapper.writeValueAsString(results));
        });
    }

    private McpTool confluenceFindPage() {
        ObjectNode schema = InputSchema.object()
                .str("spaceKey", "Ключ пространства (по умолчанию из настроек)", false)
                .str("title", "Точный заголовок страницы", true)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_find_page",
                "Найти ID страницы по пространству и точному заголовку.", schema, args -> {
            ConfluenceClient client = confluence(args);
            Optional<String> id = client.findPageId(requireSpace(args), reqStr(args, "title"));
            ObjectNode out = mapper.createObjectNode();
            out.put("pageId", id.orElse(null));
            return ToolResult.ok(mapper.writeValueAsString(out));
        });
    }

    private McpTool confluenceGetChildPages() {
        ObjectNode schema = InputSchema.object()
                .str("pageId", "ID родительской страницы", true)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_get_child_pages",
                "Получить ID прямых дочерних страниц.", schema, args -> {
            ConfluenceClient client = confluence(args);
            List<String> ids = client.getChildPageIds(reqStr(args, "pageId"));
            return ToolResult.ok(mapper.writeValueAsString(ids));
        });
    }

    private McpTool confluenceGetUser() {
        ObjectNode schema = InputSchema.object()
                .str("userKey", "Ключ пользователя Confluence", true)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_get_user",
                "Получить пользователя Confluence по ключу.", schema, args -> {
            ConfluenceClient client = confluence(args);
            LinkResult user = client.user(reqStr(args, "userKey"));
            return ToolResult.ok(mapper.writeValueAsString(user));
        });
    }

    // ---- Confluence export engine (наша фишка) ----------------------------

    private McpTool confluenceExportTreeToAdoc() {
        ObjectNode schema = InputSchema.object()
                .str("pageId", "ID страницы (либо url)", false)
                .str("url", "URL страницы (?pageId=… или /display/SPACE/Title), если нет pageId", false)
                .str("targetDir", "Абсолютный путь папки назначения", true)
                .bool("includeChildren", "Выгружать дерево дочерних (по умолчанию true)", false)
                .bool("includeAttachments", "Скачивать вложения (по умолчанию true)", false)
                .bool("exportColors", "Сохранять оригинальные цвета (по умолчанию false)", false)
                .bool("debug", "Сохранять папку source/ (по умолчанию false)", false)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_export_tree_to_adoc",
                "Экспортировать страницу Confluence и её поддерево в локальное дерево AsciiDoc "
                        + "(читает Confluence, пишет файлы; саму страницу не меняет).", schema, args -> {
            ConfluenceClient client = confluence(args);
            String pageId = resolvePageId(client, text(args, "pageId"), text(args, "url"));
            DispatcherPage dp = new DispatcherPage(client, Path.of(reqStr(args, "targetDir")),
                    ObjectMapperExt.INSTANT);
            dp.setIncludeChildren(optBool(args, "includeChildren", true));
            dp.setIncludeAttachments(optBool(args, "includeAttachments", true));
            dp.setExportColors(optBool(args, "exportColors", false));
            dp.setDebug(optBool(args, "debug", false));
            String title = dp.generate(pageId, (t, s) -> { });
            ObjectNode out = mapper.createObjectNode();
            out.put("title", title);
            out.put("pageId", pageId);
            out.put("outputDir", String.valueOf(dp.getDestination()));
            return ToolResult.ok(mapper.writeValueAsString(out));
        });
    }

    private McpTool confluencePublishAdoc() {
        ObjectNode schema = InputSchema.object()
                .str("source", "Абсолютный путь .adoc файла или папки с .adoc", true)
                .str("url", "URL целевой страницы (?pageId=… или /display/SPACE/Title); для папки — родительская страница", false)
                .str("pageId", "ID целевой страницы (альтернатива url)", false)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_publish_adoc",
                "Опубликовать AsciiDoc (файл или папку) в Confluence: рендер в storage format и заливка "
                        + "через REST. ИЗМЕНЯЕТ страницы Confluence.", schema, args -> {
            AtlassianEndpoint ep = endpoint(args);
            ru.gitverse.adoct.generate.confluence.ConfluenceClient client =
                    new ru.gitverse.adoct.generate.confluence.ConfluenceClient(ep.host(), ep.token());
            String url = text(args, "url");
            if (url == null || url.isBlank()) {
                String pageId = text(args, "pageId");
                url = pageId == null || pageId.isBlank() ? "" : "pageId=" + pageId;
            }
            String result = new AdocPublisher(client).publish(url, Path.of(reqStr(args, "source")));
            ObjectNode out = mapper.createObjectNode();
            out.put("result", result);
            return ToolResult.ok(mapper.writeValueAsString(out));
        });
    }

    // ---- helpers ----------------------------------------------------------

    private AtlassianEndpoint endpoint(JsonNode args) {
        String host = text(args, "host");
        return endpoints.forHost(host).orElseThrow(() -> new IllegalArgumentException(
                "Не настроена точка подключения Atlassian"
                        + (host == null || host.isBlank() ? "" : " для хоста '" + host + "'")
                        + "; настроенные хосты: " + endpoints.all().stream().map(AtlassianEndpoint::host).toList()));
    }

    private ConfluenceClient confluence(JsonNode args) {
        AtlassianEndpoint ep = endpoint(args);
        return new ConfluenceClient(ep.host(), ep.token());
    }

    private JiraClient jira(JsonNode args) {
        AtlassianEndpoint ep = endpoint(args);
        return new JiraClient(ep.host(), ep.token());
    }

    /** Проект Jira: из аргумента или дефолтный из настроек; ошибка, если нет ни того ни другого. */
    private String requireProject(JsonNode args) {
        String project = firstNonBlank(text(args, "projectKey"), endpoints.defaultJiraProject().orElse(null));
        if (project == null || project.isBlank()) {
            throw new IllegalArgumentException("Не задан projectKey и не настроен проект Jira по умолчанию");
        }
        return project;
    }

    /** Пространство Confluence: из аргумента или дефолтное из настроек; ошибка, если нет ни того ни другого. */
    private String requireSpace(JsonNode args) {
        String space = firstNonBlank(text(args, "spaceKey"), endpoints.defaultConfluenceSpace().orElse(null));
        if (space == null || space.isBlank()) {
            throw new IllegalArgumentException("Не задан spaceKey и не настроено пространство Confluence по умолчанию");
        }
        return space;
    }

    static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static String resolvePageId(ConfluenceClient client, String pageId, String url)
            throws IOException, InterruptedException {
        if (pageId != null && !pageId.isBlank()) {
            return pageId.trim();
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Нужен pageId или url");
        }
        Matcher byId = PAGE_ID.matcher(url);
        if (byId.find()) {
            return byId.group(1);
        }
        Matcher display = DISPLAY.matcher(url);
        if (display.find()) {
            String space = URLDecoder.decode(display.group(1), StandardCharsets.UTF_8);
            String title = URLDecoder.decode(display.group(2), StandardCharsets.UTF_8);
            return client.findPageId(space, title)
                    .orElseThrow(() -> new IllegalArgumentException("Страница не найдена: " + url));
        }
        throw new IllegalArgumentException("Не удалось извлечь pageId из url: " + url);
    }

    static String text(JsonNode args, String name) {
        JsonNode n = args == null ? null : args.get(name);
        return n == null || n.isNull() ? null : n.asText();
    }

    static String reqStr(JsonNode args, String name) {
        String v = text(args, name);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Отсутствует обязательный параметр: " + name);
        }
        return v;
    }

    static boolean optBool(JsonNode args, String name, boolean def) {
        JsonNode n = args == null ? null : args.get(name);
        return n == null || n.isNull() ? def : n.asBoolean(def);
    }

    static int optInt(JsonNode args, String name, int def) {
        JsonNode n = args == null ? null : args.get(name);
        return n == null || n.isNull() ? def : n.asInt(def);
    }
}
