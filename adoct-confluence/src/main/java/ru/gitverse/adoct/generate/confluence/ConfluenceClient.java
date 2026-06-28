package ru.gitverse.adoct.generate.confluence;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Клиент REST API Confluence Server/Data Center (поколение ~2022).
 *
 * <p>Аутентификация — Personal Access Token (заголовок {@code Authorization: Bearer ...}).
 * Использует встроенный {@link HttpClient}, так что внешних HTTP-зависимостей нет.
 */
public final class ConfluenceClient {

    private static final String MULTIPART_BOUNDARY = "----confluentMultipartBoundary7d01f";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final String baseUrl;
    private final String token;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public ConfluenceClient(String baseUrl, String token) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token;
    }

    /** Читает текущий заголовок и номер версии страницы. */
    public PageVersion getPage(String pageId) throws IOException, InterruptedException {
        HttpRequest request = baseRequest("/rest/api/content/" + pageId + "?expand=version")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response, "получить страницу " + pageId);
        JsonNode root = mapper.readTree(response.body());
        String title = root.path("title").asText();
        int number = root.path("version").path("number").asInt();
        return new PageVersion(title, number);
    }

    /**
     * Находит ID страницы по ключу пространства и точному заголовку (как в «человеческих» URL
     * вида {@code /display/SPACE/Title}, где номера страницы нет).
     *
     * @return ID первой подходящей страницы, либо {@code null}, если такой страницы нет
     */
    public String findPageId(String spaceKey, String title) throws IOException, InterruptedException {
        String query = "/rest/api/content?type=page&limit=1"
                + "&spaceKey=" + URLEncoder.encode(spaceKey, StandardCharsets.UTF_8)
                + "&title=" + URLEncoder.encode(title, StandardCharsets.UTF_8);
        HttpRequest request = baseRequest(query).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response, "найти страницу «" + title + "» в пространстве " + spaceKey);
        JsonNode results = mapper.readTree(response.body()).path("results");
        if (results.isArray() && !results.isEmpty()) {
            return results.get(0).path("id").asText();
        }
        return null;
    }

    /** Возвращает ключ пространства (space key), которому принадлежит страница. */
    public String getSpaceKey(String pageId) throws IOException, InterruptedException {
        HttpRequest request = baseRequest("/rest/api/content/" + pageId + "?expand=space")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response, "получить пространство страницы " + pageId);
        return mapper.readTree(response.body()).path("space").path("key").asText();
    }

    /**
     * Создаёт новую страницу как потомка {@code parentId} в пространстве {@code spaceKey}.
     *
     * @return ID созданной страницы
     */
    public String createPage(String spaceKey, String parentId, String title, String storageXhtml)
            throws IOException, InterruptedException {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("type", "page");
        payload.put("title", title);
        payload.putObject("space").put("key", spaceKey);
        ArrayNode ancestors = payload.putArray("ancestors");
        ancestors.addObject().put("id", parentId);
        ObjectNode storage = payload.putObject("body").putObject("storage");
        storage.put("value", storageXhtml);
        storage.put("representation", "storage");

        HttpRequest request = baseRequest("/rest/api/content")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response, "создать страницу «" + title + "»");
        return mapper.readTree(response.body()).path("id").asText();
    }

    /**
     * Загружает файл как вложение страницы (multipart, поле {@code file}).
     *
     * <p>Confluence не даёт создать вложение с уже существующим именем (POST на
     * {@code .../child/attachment} вернёт HTTP 400 «Cannot add a new attachment with same file
     * name»), поэтому сначала ищем вложение по имени: если есть — POST новой версии на
     * {@code .../child/attachment/{id}/data}, иначе создаём.
     */
    public void uploadAttachment(String pageId, Path file) throws IOException, InterruptedException {
        String fileName = file.getFileName().toString();
        byte[] body = buildMultipartBody(fileName, Files.readAllBytes(file));
        String existingId = findAttachmentId(pageId, fileName);
        String path = existingId == null
                ? "/rest/api/content/" + pageId + "/child/attachment"
                : "/rest/api/content/" + pageId + "/child/attachment/" + existingId + "/data";
        HttpRequest request = baseRequest(path)
                .header("X-Atlassian-Token", "nocheck")
                .header("Content-Type", "multipart/form-data; boundary=" + MULTIPART_BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response, "загрузить вложение " + fileName);
    }

    /** ID существующего вложения страницы с данным именем, либо {@code null}, если его нет. */
    private String findAttachmentId(String pageId, String fileName) throws IOException, InterruptedException {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        HttpRequest request = baseRequest("/rest/api/content/" + pageId + "/child/attachment?filename=" + encoded)
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response, "получить вложения страницы " + pageId);
        JsonNode results = mapper.readTree(response.body()).path("results");
        if (results.isArray() && !results.isEmpty()) {
            return results.get(0).path("id").asText();
        }
        return null;
    }

    /** Обновляет тело и заголовок страницы, поднимая версию до {@code newVersion}. */
    public void updatePage(String pageId, String title, String storageXhtml, int newVersion)
            throws IOException, InterruptedException {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("id", pageId);
        payload.put("type", "page");
        payload.put("title", title);
        payload.putObject("version").put("number", newVersion);
        ObjectNode storage = payload.putObject("body").putObject("storage");
        storage.put("value", storageXhtml);
        storage.put("representation", "storage");

        HttpRequest request = baseRequest("/rest/api/content/" + pageId)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response, "обновить страницу " + pageId);
    }

    /** Проставляет странице глобальные метки (labels). Пустой список — запрос не шлётся. */
    public void addLabels(String pageId, List<String> labels) throws IOException, InterruptedException {
        if (labels.isEmpty()) {
            return;
        }
        ArrayNode payload = mapper.createArrayNode();
        for (String label : labels) {
            ObjectNode node = payload.addObject();
            node.put("prefix", "global");
            node.put("name", label);
        }
        HttpRequest request = baseRequest("/rest/api/content/" + pageId + "/label")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response, "проставить метки страницы " + pageId);
    }

    /** Значение content-property страницы по ключу, либо {@code null}, если свойства нет (404). */
    public String getProperty(String pageId, String key) throws IOException, InterruptedException {
        HttpRequest request = baseRequest("/rest/api/content/" + pageId + "/property/" + key).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 404) {
            return null;
        }
        ensureSuccess(response, "получить свойство " + key + " страницы " + pageId);
        JsonNode value = mapper.readTree(response.body()).path("value");
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    /** Создаёт/перезаписывает content-property (delete + create, чтобы не вести версии свойства). */
    public void setProperty(String pageId, String key, String value) throws IOException, InterruptedException {
        deleteProperty(pageId, key);
        ObjectNode payload = mapper.createObjectNode();
        payload.put("key", key);
        payload.put("value", value);
        HttpRequest request = baseRequest("/rest/api/content/" + pageId + "/property")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response, "записать свойство " + key + " страницы " + pageId);
    }

    /** Удаляет content-property; отсутствие свойства (404) не считается ошибкой. */
    public void deleteProperty(String pageId, String key) throws IOException, InterruptedException {
        HttpRequest request = baseRequest("/rest/api/content/" + pageId + "/property/" + key).DELETE().build();
        http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpRequest.Builder baseRequest(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + token);
    }

    private static byte[] buildMultipartBody(String fileName, byte[] fileBytes) throws IOException {
        String prefix = "--" + MULTIPART_BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
        String suffix = "\r\n--" + MULTIPART_BOUNDARY + "--\r\n";
        var buffer = new java.io.ByteArrayOutputStream();
        buffer.write(prefix.getBytes(StandardCharsets.UTF_8));
        buffer.write(fileBytes);
        buffer.write(suffix.getBytes(StandardCharsets.UTF_8));
        return buffer.toByteArray();
    }

    private static void ensureSuccess(HttpResponse<String> response, String action) throws IOException {
        int code = response.statusCode();
        if (code < 200 || code >= 300) {
            String reason = errorReason(response.body());
            throw new IOException("Не удалось " + action + ": HTTP " + code + " — "
                    + (reason != null ? reason : response.body()));
        }
    }

    /** Достаёт человекочитаемое поле {@code message} из JSON-тела ошибки Confluence, иначе {@code null}. */
    static String errorReason(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode message = new ObjectMapper().readTree(responseBody).get("message");
            return message != null && message.isTextual() ? message.asText() : null;
        } catch (IOException e) {
            return null;
        }
    }
}
