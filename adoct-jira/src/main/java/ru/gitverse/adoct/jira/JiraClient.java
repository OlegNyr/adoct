package ru.gitverse.adoct.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Клиент REST API Jira Server/Data Center (поколение ~2022, {@code /rest/api/2}).
 *
 * <p>Аутентификация — Personal Access Token (заголовок {@code Authorization: Bearer ...}).
 * Использует встроенный {@link HttpClient}, без внешних HTTP-зависимостей.
 */
public final class JiraClient {

    /** Поля, которые переносим между задачами (этап 1 — минимальный набор). */
    private static final String FIELDS = "project,issuetype,summary,description";

    private final String baseUrl;
    private final String token;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public JiraClient(String baseUrl, String token) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token;
    }

    /**
     * Читает задачу и формирует payload, готовый для создания новой:
     * {@code {"fields": {"project": {"key": ...}, "issuetype": {"name": ...}, "summary": ..., "description": ...}}}.
     */
    public ObjectNode exportCreatePayload(String issueKey) throws IOException, InterruptedException {
        HttpRequest request = baseRequest("/rest/api/2/issue/" + issueKey + "?fields=" + FIELDS)
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response, "получить задачу " + issueKey);

        JsonNode fields = mapper.readTree(response.body()).path("fields");
        ObjectNode payload = mapper.createObjectNode();
        ObjectNode out = payload.putObject("fields");
        out.putObject("project").put("key", fields.path("project").path("key").asText());
        out.putObject("issuetype").put("name", fields.path("issuetype").path("name").asText());
        out.put("summary", fields.path("summary").asText());
        JsonNode description = fields.path("description");
        if (description.isTextual()) {
            out.put("description", description.asText());
        }
        return payload;
    }

    /**
     * Читает задачу целиком. {@code fields} — список полей через запятую (например {@code "summary,status"})
     * или {@code "*all"}; при пустом значении берётся базовый набор {@link #FIELDS}.
     *
     * @return корневой JSON ответа Jira (вызывающий читает {@code .path("fields")})
     */
    public JsonNode getIssue(String issueKey, String fields) throws IOException, InterruptedException {
        String wanted = (fields == null || fields.isBlank()) ? FIELDS : fields.trim();
        HttpRequest request = baseRequest("/rest/api/2/issue/" + issueKey
                + "?fields=" + URLEncoder.encode(wanted, StandardCharsets.UTF_8))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response, "получить задачу " + issueKey);
        return mapper.readTree(response.body());
    }

    /**
     * Ищет задачи по JQL. {@code maxResults} ограничивается диапазоном 1..100 (по умолчанию 50 при &le; 0).
     *
     * @return корневой JSON ответа поиска ({@code total}, {@code issues[]})
     */
    public JsonNode searchJql(String jql, int maxResults) throws IOException, InterruptedException {
        int limit = maxResults <= 0 ? 50 : Math.min(maxResults, 100);
        HttpRequest request = baseRequest("/rest/api/2/search"
                + "?jql=" + URLEncoder.encode(jql, StandardCharsets.UTF_8)
                + "&maxResults=" + limit)
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response, "выполнить поиск JQL");
        return mapper.readTree(response.body());
    }

    /** Список проектов: {@code GET /rest/api/2/project} → массив проектов. */
    public JsonNode listProjects() throws IOException, InterruptedException {
        return getJson("/rest/api/2/project", "получить список проектов");
    }

    /** Текущий пользователь (владелец токена): {@code GET /rest/api/2/myself}. */
    public JsonNode getCurrentUser() throws IOException, InterruptedException {
        return getJson("/rest/api/2/myself", "получить текущего пользователя");
    }

    /** Agile-доски: {@code GET /rest/agile/1.0/board} (опц. фильтр по проекту). */
    public JsonNode listBoards(String projectKeyOrId) throws IOException, InterruptedException {
        String path = "/rest/agile/1.0/board";
        if (projectKeyOrId != null && !projectKeyOrId.isBlank()) {
            path += "?projectKeyOrId=" + URLEncoder.encode(projectKeyOrId.trim(), StandardCharsets.UTF_8);
        }
        return getJson(path, "получить список досок");
    }

    /** Спринты доски: {@code GET /rest/agile/1.0/board/{id}/sprint} (опц. фильтр по state, напр. active,future). */
    public JsonNode listSprints(String boardId, String state) throws IOException, InterruptedException {
        String path = "/rest/agile/1.0/board/" + boardId + "/sprint";
        if (state != null && !state.isBlank()) {
            path += "?state=" + URLEncoder.encode(state.trim(), StandardCharsets.UTF_8);
        }
        return getJson(path, "получить спринты доски " + boardId);
    }

    /** Задачи спринта: {@code GET /rest/agile/1.0/sprint/{id}/issue} с лимитом (1..100, по умолчанию 50). */
    public JsonNode getSprintIssues(String sprintId, int maxResults) throws IOException, InterruptedException {
        int limit = maxResults <= 0 ? 50 : Math.min(maxResults, 100);
        return getJson("/rest/agile/1.0/sprint/" + sprintId + "/issue?maxResults=" + limit,
                "получить задачи спринта " + sprintId);
    }

    /** Бэклог доски: {@code GET /rest/agile/1.0/board/{id}/backlog} с лимитом (1..100, по умолчанию 50). */
    public JsonNode getBoardBacklog(String boardId, int maxResults) throws IOException, InterruptedException {
        int limit = maxResults <= 0 ? 50 : Math.min(maxResults, 100);
        return getJson("/rest/agile/1.0/board/" + boardId + "/backlog?maxResults=" + limit,
                "получить бэклог доски " + boardId);
    }

    // ---- удаление / массовое создание ----

    /** Удаляет задачу ({@code DELETE /rest/api/2/issue/{key}}); {@code deleteSubtasks} — удалять ли подзадачи. */
    public void deleteIssue(String issueKey, boolean deleteSubtasks) throws IOException, InterruptedException {
        sendNoContent("DELETE", "/rest/api/2/issue/" + issueKey + "?deleteSubtasks=" + deleteSubtasks, null,
                "удалить задачу " + issueKey);
    }

    /** Массовое создание задач: {@code POST /rest/api/2/issue/bulk}, payload {@code {"issueUpdates":[...]}}. */
    public JsonNode createIssuesBulk(JsonNode payload) throws IOException, InterruptedException {
        return sendJson("POST", "/rest/api/2/issue/bulk", payload, "массово создать задачи");
    }

    // ---- worklog ----

    /** Журнал работ задачи: {@code GET /rest/api/2/issue/{key}/worklog}. */
    public JsonNode getWorklog(String issueKey) throws IOException, InterruptedException {
        return getJson("/rest/api/2/issue/" + issueKey + "/worklog", "получить worklog задачи " + issueKey);
    }

    /** Добавляет запись о работе: {@code POST .../worklog}, payload {@code {"timeSpent":"2h","comment":...}}. */
    public JsonNode addWorklog(String issueKey, JsonNode payload) throws IOException, InterruptedException {
        return sendJson("POST", "/rest/api/2/issue/" + issueKey + "/worklog", payload,
                "добавить worklog к задаче " + issueKey);
    }

    // ---- связи задач / epic / remote ----

    /** Типы связей: {@code GET /rest/api/2/issueLinkType}. */
    public JsonNode getLinkTypes() throws IOException, InterruptedException {
        return getJson("/rest/api/2/issueLinkType", "получить типы связей");
    }

    /** Создаёт связь между задачами: {@code POST /rest/api/2/issueLink} (payload с type/inwardIssue/outwardIssue). */
    public void createIssueLink(JsonNode payload) throws IOException, InterruptedException {
        sendNoContent("POST", "/rest/api/2/issueLink", payload, "создать связь задач");
    }

    /** Удаляет связь: {@code DELETE /rest/api/2/issueLink/{id}}. */
    public void removeIssueLink(String linkId) throws IOException, InterruptedException {
        sendNoContent("DELETE", "/rest/api/2/issueLink/" + linkId, null, "удалить связь " + linkId);
    }

    /** Внешняя ссылка задачи: {@code POST /rest/api/2/issue/{key}/remotelink} (payload с object.url/title). */
    public JsonNode createRemoteIssueLink(String issueKey, JsonNode payload)
            throws IOException, InterruptedException {
        return sendJson("POST", "/rest/api/2/issue/" + issueKey + "/remotelink", payload,
                "создать внешнюю ссылку для " + issueKey);
    }

    /** Привязывает задачи к эпику: {@code POST /rest/agile/1.0/epic/{epicKey}/issue} body {@code {"issues":[...]}}. */
    public void linkToEpic(String epicKey, List<String> issueKeys) throws IOException, InterruptedException {
        ObjectNode payload = mapper.createObjectNode();
        ArrayNode issues = payload.putArray("issues");
        issueKeys.forEach(issues::add);
        sendNoContent("POST", "/rest/agile/1.0/epic/" + epicKey + "/issue", payload,
                "привязать задачи к эпику " + epicKey);
    }

    // ---- watchers ----

    /** Наблюдатели задачи: {@code GET /rest/api/2/issue/{key}/watchers}. */
    public JsonNode getWatchers(String issueKey) throws IOException, InterruptedException {
        return getJson("/rest/api/2/issue/" + issueKey + "/watchers", "получить наблюдателей " + issueKey);
    }

    /** Добавляет наблюдателя ({@code POST .../watchers}, тело — username строкой). */
    public void addWatcher(String issueKey, String username) throws IOException, InterruptedException {
        sendNoContent("POST", "/rest/api/2/issue/" + issueKey + "/watchers", mapper.valueToTree(username),
                "добавить наблюдателя к " + issueKey);
    }

    /** Удаляет наблюдателя ({@code DELETE .../watchers?username=}). */
    public void removeWatcher(String issueKey, String username) throws IOException, InterruptedException {
        sendNoContent("DELETE", "/rest/api/2/issue/" + issueKey + "/watchers?username="
                + URLEncoder.encode(username, StandardCharsets.UTF_8), null, "удалить наблюдателя у " + issueKey);
    }

    // ---- версии / компоненты проекта ----

    /** Версии проекта: {@code GET /rest/api/2/project/{key}/versions}. */
    public JsonNode getProjectVersions(String projectKey) throws IOException, InterruptedException {
        return getJson("/rest/api/2/project/" + projectKey + "/versions", "получить версии проекта " + projectKey);
    }

    /** Создаёт версию проекта: {@code POST /rest/api/2/version} (payload с name/project). */
    public JsonNode createVersion(JsonNode payload) throws IOException, InterruptedException {
        return sendJson("POST", "/rest/api/2/version", payload, "создать версию");
    }

    /** Компоненты проекта: {@code GET /rest/api/2/project/{key}/components}. */
    public JsonNode getProjectComponents(String projectKey) throws IOException, InterruptedException {
        return getJson("/rest/api/2/project/" + projectKey + "/components",
                "получить компоненты проекта " + projectKey);
    }

    // ---- спринты (write) ----

    /** Создаёт спринт: {@code POST /rest/agile/1.0/sprint} (payload с name/originBoardId). */
    public JsonNode createSprint(JsonNode payload) throws IOException, InterruptedException {
        return sendJson("POST", "/rest/agile/1.0/sprint", payload, "создать спринт");
    }

    /** Частично обновляет спринт: {@code POST /rest/agile/1.0/sprint/{id}} (name/state/dates). */
    public JsonNode updateSprint(String sprintId, JsonNode payload) throws IOException, InterruptedException {
        return sendJson("POST", "/rest/agile/1.0/sprint/" + sprintId, payload, "обновить спринт " + sprintId);
    }

    /** Перемещает задачи в спринт: {@code POST /rest/agile/1.0/sprint/{id}/issue} body {@code {"issues":[...]}}. */
    public void addIssuesToSprint(String sprintId, List<String> issueKeys)
            throws IOException, InterruptedException {
        ObjectNode payload = mapper.createObjectNode();
        ArrayNode issues = payload.putArray("issues");
        issueKeys.forEach(issues::add);
        sendNoContent("POST", "/rest/agile/1.0/sprint/" + sprintId + "/issue", payload,
                "добавить задачи в спринт " + sprintId);
    }

    // ---- поля / changelog / вложения ----

    /** Все поля Jira (системные и кастомные): {@code GET /rest/api/2/field}. */
    public JsonNode searchFields() throws IOException, InterruptedException {
        return getJson("/rest/api/2/field", "получить список полей");
    }

    /** История изменений задачи: {@code GET /rest/api/2/issue/{key}?expand=changelog}. */
    public JsonNode getChangelog(String issueKey) throws IOException, InterruptedException {
        return getJson("/rest/api/2/issue/" + issueKey + "?expand=changelog",
                "получить историю задачи " + issueKey);
    }

    /** Метаданные вложений задачи (имя, размер, URL): из {@code fields.attachment}. */
    public JsonNode getAttachmentsMeta(String issueKey) throws IOException, InterruptedException {
        JsonNode issue = getIssue(issueKey, "attachment");
        return issue.path("fields").path("attachment");
    }

    /**
     * Скачивает все вложения задачи в {@code targetDir}. Возвращает имена сохранённых файлов.
     */
    public List<String> downloadAttachments(String issueKey, Path targetDir)
            throws IOException, InterruptedException {
        Files.createDirectories(targetDir);
        JsonNode attachments = getAttachmentsMeta(issueKey);
        java.util.ArrayList<String> saved = new java.util.ArrayList<>();
        if (attachments.isArray()) {
            for (JsonNode att : attachments) {
                String fileName = att.path("filename").asText();
                String content = att.path("content").asText();
                if (fileName.isBlank() || content.isBlank()) {
                    continue;
                }
                byte[] bytes = downloadBinary(content);
                Files.write(targetDir.resolve(fileName), bytes);
                saved.add(fileName);
            }
        }
        return saved;
    }

    private byte[] downloadBinary(String absoluteUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(absoluteUrl))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        int code = response.statusCode();
        if (code < 200 || code >= 300) {
            throw new IOException("Не удалось скачать вложение: HTTP " + code + " — " + absoluteUrl);
        }
        return response.body();
    }

    // ---- generic JSON helpers ----

    private JsonNode getJson(String path, String action) throws IOException, InterruptedException {
        HttpRequest request = baseRequest(path).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response, action);
        return mapper.readTree(response.body());
    }

    /** Шлёт {@code method} с JSON-телом и возвращает разобранный ответ (или {@code null}, если тело пустое). */
    private JsonNode sendJson(String method, String path, JsonNode payload, String action)
            throws IOException, InterruptedException {
        HttpResponse<String> response = exchange(method, path, payload, action);
        String body = response.body();
        return body == null || body.isBlank() ? null : mapper.readTree(body);
    }

    /** Шлёт {@code method} с JSON-телом, игнорируя тело ответа (для 2xx/204). */
    private void sendNoContent(String method, String path, JsonNode payload, String action)
            throws IOException, InterruptedException {
        exchange(method, path, payload, action);
    }

    private HttpResponse<String> exchange(String method, String path, JsonNode payload, String action)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = baseRequest(path);
        HttpRequest.BodyPublisher pub = payload == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8);
        if (payload != null) {
            builder.header("Content-Type", "application/json");
        }
        HttpRequest request = builder.method(method, pub).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response, action);
        return response;
    }

    /** Создаёт задачу из payload {@code {"fields": {...}}} и возвращает ключ новой задачи. */
    public String createIssue(JsonNode payload) throws IOException, InterruptedException {
        HttpRequest request = baseRequest("/rest/api/2/issue")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response, "создать задачу");
        return mapper.readTree(response.body()).path("key").asText();
    }

    /** Обновляет задачу payload-ом {@code {"fields": {...}}} / {@code {"update": {...}}} (PUT, ответ 204). */
    public void updateIssue(String issueKey, JsonNode payload) throws IOException, InterruptedException {
        HttpRequest request = baseRequest("/rest/api/2/issue/" + issueKey)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response, "обновить задачу " + issueKey);
    }

    /** Доступные переходы по workflow для задачи: {@code GET .../transitions} → {@code {transitions:[{id,name,...}]}}. */
    public JsonNode getTransitions(String issueKey) throws IOException, InterruptedException {
        HttpRequest request = baseRequest("/rest/api/2/issue/" + issueKey + "/transitions")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response, "получить переходы задачи " + issueKey);
        return mapper.readTree(response.body());
    }

    /** Переводит задачу по переходу {@code transitionId} (POST {@code .../transitions}, ответ 204). */
    public void transitionIssue(String issueKey, String transitionId) throws IOException, InterruptedException {
        ObjectNode payload = mapper.createObjectNode();
        payload.putObject("transition").put("id", transitionId);
        HttpRequest request = baseRequest("/rest/api/2/issue/" + issueKey + "/transitions")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response, "перевести задачу " + issueKey);
    }

    /** Добавляет комментарий к задаче ({@code POST .../comment}); возвращает JSON созданного комментария. */
    public JsonNode addComment(String issueKey, String body) throws IOException, InterruptedException {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("body", body);
        HttpRequest request = baseRequest("/rest/api/2/issue/" + issueKey + "/comment")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response, "добавить комментарий к задаче " + issueKey);
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
