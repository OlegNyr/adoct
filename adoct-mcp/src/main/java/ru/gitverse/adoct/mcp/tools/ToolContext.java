package ru.gitverse.adoct.mcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.jira.JiraClient;
import ru.gitverse.adoct.mcp.AtlassianEndpoint;
import ru.gitverse.adoct.mcp.AtlassianKind;
import ru.gitverse.adoct.mcp.EndpointSupplier;
import ru.gitverse.adoct.mcp.TeamMember;
import ru.gitverse.adoct.mcp.Template;
import ru.gitverse.adoct.mcp.ToolResult;
import ru.gitverse.adoct.parser.ConvertStorageToAdoc;
import ru.gitverse.adoct.parser.DispatcherPage;
import ru.gitverse.adoct.parser.confluence.ConfluenceClient;
import ru.gitverse.adoct.parser.confluence.ContentPage;
import ru.gitverse.adoct.parser.confluence.ObjectMapperExt;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Общий контекст инструментов MCP: фабрики REST-клиентов, резолв доступов через {@link EndpointSupplier}
 * и утилиты разбора аргументов/сериализации. Один экземпляр создаётся {@link ToolRegistry} и передаётся
 * каждому {@link Tool#create(ToolContext)} — чтобы сами инструменты оставались тонкими.
 *
 * <p>Резолв точки подключения: необязательный аргумент {@code host} → {@link EndpointSupplier#forHost(String)},
 * иначе точка по умолчанию. Для «однопроектной» инсталляции есть дефолтные проект Jira и пространство Confluence.
 */
public final class ToolContext {

    private static final Pattern PAGE_ID = Pattern.compile("pageId=(\\d+)");
    private static final Pattern DISPLAY = Pattern.compile("/display/([^/?#]+)/([^/?#]+)");
    private static final Pattern SPACE_PATH = Pattern.compile("/(?:display|spaces)/([^/?#]+)");
    private static final Pattern SPACE_QUERY = Pattern.compile("[?&]spaceKey=([^&#]+)");

    private final EndpointSupplier endpoints;
    private final ObjectMapper mapper = ObjectMapperExt.INSTANT;

    public ToolContext(EndpointSupplier endpoints) {
        this.endpoints = endpoints;
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    // ---- доступы / клиенты ----

    /**
     * Точка подключения по аргументу {@code host}; если {@code host} не задан — точка по умолчанию для
     * {@code kind} ({@code jira_*} → Jira-хост, {@code confluence_*} → Confluence-хост). Ошибка, если
     * ничего не настроено.
     */
    public AtlassianEndpoint endpoint(JsonNode args, AtlassianKind kind) {
        String host = text(args, "host");
        Optional<AtlassianEndpoint> resolved = host == null || host.isBlank()
                ? endpoints.defaultEndpoint(kind)
                : endpoints.forHost(host);
        return resolved.orElseThrow(() -> new IllegalArgumentException(
                "Не настроена точка подключения Atlassian"
                        + (host == null || host.isBlank() ? "" : " для хоста '" + host + "'")
                        + "; настроенные хосты: " + endpoints.all().stream().map(AtlassianEndpoint::host).toList()));
    }

    /** Jira-клиент для выбранной точки (по умолчанию — Jira-хост). */
    public JiraClient jira(JsonNode args) {
        AtlassianEndpoint ep = endpoint(args, AtlassianKind.JIRA);
        return new JiraClient(ep.host(), ep.token());
    }

    /** Confluence-клиент чтения/экспорта (parser-движок); по умолчанию — Confluence-хост. */
    public ConfluenceClient confluence(JsonNode args) {
        AtlassianEndpoint ep = endpoint(args, AtlassianKind.CONFLUENCE);
        return new ConfluenceClient(ep.host(), ep.token());
    }

    /** Confluence-клиент записи/публикации (generate-движок); по умолчанию — Confluence-хост. */
    public ru.gitverse.adoct.generate.confluence.ConfluenceClient confluencePublish(JsonNode args) {
        AtlassianEndpoint ep = endpoint(args, AtlassianKind.CONFLUENCE);
        return new ru.gitverse.adoct.generate.confluence.ConfluenceClient(ep.host(), ep.token());
    }

    /** Дефолтный проект Jira из настроек (если задан). */
    public Optional<String> defaultJiraProject() {
        return endpoints.defaultJiraProject();
    }

    /**
     * Дефолтное пространство Confluence из настроек (если задано). В настройках значение можно задавать
     * как полный путь/URL страницы — отсюда извлекается только ключ пространства (см. {@link #spaceKeyOf}).
     */
    public Optional<String> defaultConfluenceSpace() {
        return endpoints.defaultConfluenceSpace().map(ToolContext::spaceKeyOf).filter(s -> !s.isBlank());
    }

    /**
     * Извлекает ключ пространства из произвольной формы: полного URL/пути
     * ({@code …/display/KEY/Title}, {@code …/spaces/KEY/…}, {@code …?spaceKey=KEY}) или голого ключа.
     */
    public static String spaceKeyOf(String value) {
        if (value == null) {
            return "";
        }
        String s = value.trim();
        if (s.isEmpty()) {
            return "";
        }
        Matcher q = SPACE_QUERY.matcher(s);
        if (q.find()) {
            return URLDecoder.decode(q.group(1), StandardCharsets.UTF_8);
        }
        Matcher p = SPACE_PATH.matcher(s);
        if (p.find()) {
            return URLDecoder.decode(p.group(1), StandardCharsets.UTF_8);
        }
        return s;
    }

    /** Ростер команды из настроек. */
    public List<TeamMember> team() {
        return endpoints.team();
    }

    /** Шаблоны задач из настроек. */
    public List<Template> templates() {
        return endpoints.templates();
    }

    /** PlantUML-диаграмма состояний задач из настроек. */
    public String workflowDiagram() {
        return endpoints.workflowDiagram();
    }

    /** Проект Jira: из аргумента {@code projectKey} или дефолтный; ошибка, если ни того ни другого. */
    public String requireProject(JsonNode args) {
        String project = firstNonBlank(text(args, "projectKey"), defaultJiraProject().orElse(null));
        if (project == null || project.isBlank()) {
            throw new IllegalArgumentException("Не задан projectKey и не настроен проект Jira по умолчанию");
        }
        return project;
    }

    /** Пространство Confluence: из аргумента {@code spaceKey} или дефолтное; ошибка, если ни того ни другого. */
    public String requireSpace(JsonNode args) {
        String space = firstNonBlank(text(args, "spaceKey"), defaultConfluenceSpace().orElse(null));
        if (space == null || space.isBlank()) {
            throw new IllegalArgumentException("Не задан spaceKey и не настроено пространство Confluence по умолчанию");
        }
        return space;
    }

    /**
     * Резолвит pageId: явный {@code pageId}, иначе из {@code url} ({@code pageId=} или {@code /display/SPACE/Title}
     * с дорезолвом по REST).
     */
    public String resolvePageId(ConfluenceClient client, String pageId, String url)
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

    /**
     * Конвертирует уже загруженную страницу Confluence в AsciiDoc нашим движком и возвращает текст —
     * полностью в памяти, без записи файлов и без загрузки вложений/картинок. {@code fast=true} —
     * без доп. REST на резолв ссылок (для подачи контекста): ссылки резолвятся только локально.
     */
    public String pageToAdoc(ConfluenceClient client, String pageId, ContentPage page, boolean fast) {
        return new DispatcherPage(client, Path.of(System.getProperty("java.io.tmpdir")), mapper)
                .toAdoc(pageId, page, fast);
    }

    /** Конвертирует произвольный storage-фрагмент (например тело версии) в AsciiDoc, in-memory. */
    public String storageToAdoc(String storage, String title) {
        return new ConvertStorageToAdoc(storage, "").toAdoc(title);
    }

    // ---- результат ----

    /** Оборачивает значение в успешный результат: строку — как есть, иначе сериализует в JSON. */
    public ToolResult ok(Object value) throws JsonProcessingException {
        return ToolResult.ok(value instanceof String s ? s : mapper.writeValueAsString(value));
    }

    /** Строит узел {@code {version, storage}} из тела версии страницы Confluence. */
    public ObjectNode versionStorage(JsonNode content) {
        ObjectNode o = mapper.createObjectNode();
        o.put("version", content.path("version").path("number").asInt());
        o.put("storage", content.path("body").path("storage").path("value").asText());
        return o;
    }

    // ---- разбор аргументов ----

    /** Значение строкового аргумента или {@code null}. */
    public String text(JsonNode args, String name) {
        JsonNode n = args == null ? null : args.get(name);
        return n == null || n.isNull() ? null : n.asText();
    }

    /** Обязательный строковый аргумент; ошибка, если пуст. */
    public String reqStr(JsonNode args, String name) {
        String v = text(args, name);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Отсутствует обязательный параметр: " + name);
        }
        return v;
    }

    /** Булев аргумент с дефолтом. */
    public boolean optBool(JsonNode args, String name, boolean def) {
        JsonNode n = args == null ? null : args.get(name);
        return n == null || n.isNull() ? def : n.asBoolean(def);
    }

    /** Целочисленный аргумент с дефолтом. */
    public int optInt(JsonNode args, String name, int def) {
        JsonNode n = args == null ? null : args.get(name);
        return n == null || n.isNull() ? def : n.asInt(def);
    }

    /** Обязательный целочисленный аргумент (число или строка-число). */
    public int reqInt(JsonNode args, String name) {
        JsonNode n = args == null ? null : args.get(name);
        if (n == null || n.isNull()) {
            throw new IllegalArgumentException("Отсутствует обязательный параметр: " + name);
        }
        if (n.isNumber()) {
            return n.asInt();
        }
        try {
            return Integer.parseInt(n.asText().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Параметр " + name + " должен быть числом");
        }
    }

    /** Необязательный целочисленный аргумент или {@code null}. */
    public Integer optInteger(JsonNode args, String name) {
        JsonNode n = args == null ? null : args.get(name);
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isNumber()) {
            return n.asInt();
        }
        try {
            return Integer.parseInt(n.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Массив строк из аргумента; ошибка, если это не массив. */
    public List<String> strList(JsonNode args, String name) {
        JsonNode n = args == null ? null : args.get(name);
        if (n == null || !n.isArray()) {
            throw new IllegalArgumentException("Параметр " + name + " должен быть массивом");
        }
        List<String> out = new ArrayList<>();
        n.forEach(e -> out.add(e.asText()));
        return out;
    }

    /** Первое непустое из двух значений. */
    public String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    /** Кладёт строковое поле в payload, только если значение непустое. */
    public void putIfPresent(ObjectNode node, String key, String value) {
        if (value != null && !value.isBlank()) {
            node.put(key, value);
        }
    }

    /** Оборачивает текст в storage-абзац {@code <p>…</p>} с XML-экранированием. */
    public String paragraph(String text) {
        return "<p>" + xmlEscape(text) + "</p>";
    }

    private static String xmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
