package ru.gitverse.adoct.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

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
