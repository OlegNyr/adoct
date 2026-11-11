package io.github.adoct.bitbucket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Клиент REST API Bitbucket Server/Data Center ({@code /rest/api/1.0} и поиск {@code /rest/search/latest}).
 *
 * <p>Аутентификация — Personal Access Token (заголовок {@code Authorization: Bearer ...}).
 * Использует встроенный {@link HttpClient}; методы возвращают сырой {@link JsonNode}. Единственная
 * запись — создание pull request'а ({@link #createPullRequest(NewPullRequest)}).
 */
public final class BitbucketClient {

    private final String baseUrl;
    private final String token;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public BitbucketClient(String baseUrl, String token) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token;
    }

    /**
     * Поиск кода: {@code POST /rest/search/latest/search}. Ищет по содержимому и именам файлов (только
     * дефолтная ветка). Фильтр по проекту/репозиторию добавляется модификаторами запроса
     * ({@code project:KEY}, {@code repo:slug}). Потолок выдачи Bitbucket — ~1000 результатов.
     */
    public JsonNode searchCode(String query, String projectKey, String repoSlug, int start, int limit)
            throws IOException, InterruptedException {
        StringBuilder q = new StringBuilder(query == null ? "" : query.trim());
        if (projectKey != null && !projectKey.isBlank()) {
            q.append(" project:").append(projectKey.trim());
        }
        if (repoSlug != null && !repoSlug.isBlank()) {
            q.append(" repo:").append(repoSlug.trim());
        }
        ObjectNode payload = mapper.createObjectNode();
        payload.put("query", q.toString().trim());
        ObjectNode code = payload.putObject("entities").putObject("code");
        code.put("start", Math.max(start, 0));
        code.put("limit", limit <= 0 ? 25 : Math.min(limit, 100));
        return postJson("/rest/search/latest/search", payload, "выполнить поиск кода");
    }

    /** Проверка доступности/токена: {@code GET /rest/api/1.0/projects?limit=1} → код HTTP (без тела). */
    public int verifyToken() throws IOException, InterruptedException {
        HttpRequest request = baseRequest("/rest/api/1.0/projects?limit=1").GET().build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    /** Список проектов: {@code GET /rest/api/1.0/projects?start=&limit=}. */
    public JsonNode listProjects(int start, int limit) throws IOException, InterruptedException {
        return getJson("/rest/api/1.0/projects?" + page(start, limit), "получить список проектов");
    }

    /**
     * Репозитории: {@code GET /rest/api/1.0/projects/{key}/repos} (если задан {@code projectKey}) либо все —
     * {@code GET /rest/api/1.0/repos}.
     */
    public JsonNode listRepos(String projectKey, int start, int limit) throws IOException, InterruptedException {
        String base = projectKey != null && !projectKey.isBlank()
                ? "/rest/api/1.0/projects/" + enc(projectKey) + "/repos?"
                : "/rest/api/1.0/repos?";
        return getJson(base + page(start, limit), "получить список репозиториев");
    }

    /** Репозиторий: {@code GET /rest/api/1.0/projects/{key}/repos/{slug}}. */
    public JsonNode getRepository(String projectKey, String repoSlug) throws IOException, InterruptedException {
        return getJson(repo(projectKey, repoSlug), "получить репозиторий " + projectKey + "/" + repoSlug);
    }

    /**
     * Просмотр файла или каталога: {@code GET .../browse/{path}?at=&start=&limit=}. Для файла возвращает
     * {@code {lines:[{text}],size,isLastPage}}, для каталога — {@code {children:{values:[...]}}}.
     */
    public JsonNode browse(String projectKey, String repoSlug, String path, String at, int start, int limit)
            throws IOException, InterruptedException {
        StringBuilder url = new StringBuilder(repo(projectKey, repoSlug)).append("/browse");
        if (path != null && !path.isBlank()) {
            url.append('/').append(encPath(path));
        }
        url.append('?').append(page(start, limit));
        if (at != null && !at.isBlank()) {
            url.append("&at=").append(enc(at));
        }
        return getJson(url.toString(), "просмотреть " + projectKey + "/" + repoSlug + "/" + path);
    }

    /** Pull request'ы репозитория: {@code GET .../pull-requests?state=&start=&limit=} (OPEN/MERGED/DECLINED/ALL). */
    public JsonNode listPullRequests(String projectKey, String repoSlug, String state, int start, int limit)
            throws IOException, InterruptedException {
        String st = state == null || state.isBlank() ? "OPEN" : state.trim().toUpperCase();
        return getJson(repo(projectKey, repoSlug) + "/pull-requests?state=" + enc(st) + "&" + page(start, limit),
                "получить pull request'ы " + projectKey + "/" + repoSlug);
    }

    /** Pull request: {@code GET .../pull-requests/{id}}. */
    public JsonNode getPullRequest(String projectKey, String repoSlug, long id)
            throws IOException, InterruptedException {
        return getJson(repo(projectKey, repoSlug) + "/pull-requests/" + id, "получить pull request " + id);
    }

    /** Diff pull request'а: {@code GET .../pull-requests/{id}/diff?contextLines=}. */
    public JsonNode getPullRequestDiff(String projectKey, String repoSlug, long id, int contextLines)
            throws IOException, InterruptedException {
        String path = repo(projectKey, repoSlug) + "/pull-requests/" + id + "/diff";
        if (contextLines > 0) {
            path += "?contextLines=" + contextLines;
        }
        return getJson(path, "получить diff pull request'а " + id);
    }

    /** Активность PR (комментарии + события): {@code GET .../pull-requests/{id}/activities?start=&limit=}. */
    public JsonNode getPullRequestActivities(String projectKey, String repoSlug, long id, int start, int limit)
            throws IOException, InterruptedException {
        return getJson(repo(projectKey, repoSlug) + "/pull-requests/" + id + "/activities?" + page(start, limit),
                "получить активность pull request'а " + id);
    }

    /**
     * Создаёт pull request: {@code POST .../pull-requests}. Если целевая ветка не задана — берётся дефолтная
     * ветка репозитория ({@link #getDefaultBranch}). Возвращает созданный PR (с {@code id} и {@code links}).
     */
    public JsonNode createPullRequest(NewPullRequest pr) throws IOException, InterruptedException {
        String target = pr.toBranch() == null || pr.toBranch().isBlank()
                ? getDefaultBranch(pr.projectKey(), pr.repoSlug())
                : pr.toBranch();
        ObjectNode payload = mapper.createObjectNode();
        payload.put("title", pr.title().trim());
        if (pr.description() != null && !pr.description().isBlank()) {
            payload.put("description", pr.description());
        }
        putRef(payload.putObject("fromRef"), pr.fromBranch(), pr.sourceProjectKey(), pr.sourceRepoSlug());
        putRef(payload.putObject("toRef"), target, pr.projectKey(), pr.repoSlug());
        ArrayNode reviewers = payload.putArray("reviewers");
        pr.reviewers().forEach(name -> reviewers.addObject().putObject("user").put("name", name));
        if (pr.draft()) {
            payload.put("draft", true);
        }
        return postJson(repo(pr.projectKey(), pr.repoSlug()) + "/pull-requests", payload,
                "создать pull request " + pr.sourceRepoSlug() + ":" + pr.fromBranch() + " → " + target);
    }

    /**
     * Дефолтная ветка репозитория (полный ref, напр. {@code refs/heads/master}):
     * {@code GET .../default-branch} (Bitbucket 8+), при 404 — устаревший {@code GET .../branches/default}.
     */
    public String getDefaultBranch(String projectKey, String repoSlug) throws IOException, InterruptedException {
        String action = "получить дефолтную ветку " + projectKey + "/" + repoSlug;
        String base = repo(projectKey, repoSlug);
        Optional<JsonNode> current = getJsonUnless404(base + "/default-branch", action);
        JsonNode branch = current.isPresent() ? current.get() : getJson(base + "/branches/default", action);
        String id = branch.path("id").asText("");
        if (id.isBlank()) {
            throw new IOException("Не удалось " + action + ": в ответе нет id ветки");
        }
        return id;
    }

    // ---- helpers ----

    /** Заполняет ref PR: {@code {id, repository:{slug, project:{key}}}}. */
    private static void putRef(ObjectNode ref, String branch, String projectKey, String repoSlug) {
        ref.put("id", fullRef(branch));
        ObjectNode repository = ref.putObject("repository");
        repository.put("slug", repoSlug);
        repository.putObject("project").put("key", projectKey);
    }

    /** Короткое имя ветки → полный ref {@code refs/heads/…}; полный ref остаётся как есть. */
    private static String fullRef(String branch) {
        String b = branch.trim();
        return b.startsWith("refs/") ? b : "refs/heads/" + b;
    }


    private static String repo(String projectKey, String repoSlug) {
        return "/rest/api/1.0/projects/" + enc(projectKey) + "/repos/" + enc(repoSlug);
    }

    /** Сегмент пагинации {@code start=&limit=} (limit зажимается в 1..100, start — не меньше 0). */
    private static String page(int start, int limit) {
        return "start=" + Math.max(start, 0) + "&limit=" + (limit <= 0 ? 25 : Math.min(limit, 100));
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Кодирует путь посегментно, сохраняя разделители {@code /}. */
    private static String encPath(String path) {
        List<String> parts = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (!segment.isEmpty()) {
                parts.add(enc(segment));
            }
        }
        return String.join("/", parts);
    }

    private JsonNode getJson(String path, String action) throws IOException, InterruptedException {
        HttpRequest request = baseRequest(path).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response, action);
        return mapper.readTree(response.body());
    }

    /** Как {@link #getJson}, но 404 — не ошибка, а пустой результат (для fallback на старые эндпоинты). */
    private Optional<JsonNode> getJsonUnless404(String path, String action)
            throws IOException, InterruptedException {
        HttpRequest request = baseRequest(path).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        ensureSuccess(response, action);
        return Optional.of(mapper.readTree(response.body()));
    }

    private JsonNode postJson(String path, JsonNode payload, String action)
            throws IOException, InterruptedException {
        HttpRequest request = baseRequest(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response, action);
        return mapper.readTree(response.body());
    }

    private HttpRequest.Builder baseRequest(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Authorization", "Bearer " + token);
    }

    private static void ensureSuccess(HttpResponse<String> response, String action) throws IOException {
        int code = response.statusCode();
        if (code < 200 || code >= 300) {
            throw new IOException("Не удалось " + action + ": HTTP " + code + " — " + response.body());
        }
    }
}
