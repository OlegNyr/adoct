package ru.gitverse.adoct.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Проверяет построение запросов и парсинг {@link JiraClient} на локальном stub-сервере (без сети). */
public class JiraClientTest {

    private HttpServer server;
    private JiraClient client;
    private final AtomicReference<String> lastUri = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private String responseBody = "{}";

    @Before
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastUri.set(exchange.getRequestURI().toString());
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        client = new JiraClient("http://127.0.0.1:" + server.getAddress().getPort(), "tok");
    }

    @After
    public void tearDown() {
        server.stop(0);
    }

    @Test
    public void getIssueUsesDefaultFieldsAndBearer() throws Exception {
        responseBody = "{\"key\":\"ABC-1\",\"fields\":{\"summary\":\"hi\"}}";
        JsonNode root = client.getIssue("ABC-1", null);

        assertEquals("Bearer tok", lastAuth.get());
        // FIELDS=project,issuetype,summary,description — запятые URL-кодируются как %2C
        assertEquals("/rest/api/2/issue/ABC-1?fields=project%2Cissuetype%2Csummary%2Cdescription", lastUri.get());
        assertEquals("hi", root.path("fields").path("summary").asText());
    }

    @Test
    public void getIssuePassesExplicitFields() throws Exception {
        client.getIssue("ABC-2", "summary,status");
        assertEquals("/rest/api/2/issue/ABC-2?fields=summary%2Cstatus", lastUri.get());
    }

    @Test
    public void searchJqlEncodesJqlAndClampsMaxResults() throws Exception {
        responseBody = "{\"total\":1,\"issues\":[{\"key\":\"ABC-1\"}]}";
        JsonNode root = client.searchJql("project = ABC ORDER BY created", 500);

        String uri = lastUri.get();
        assertTrue(uri, uri.startsWith("/rest/api/2/search?jql="));
        assertTrue(uri, uri.contains("project+%3D+ABC")); // пробелы '+' и '=' → %3D
        assertTrue(uri, uri.endsWith("&maxResults=100")); // 500 зажат до 100
        assertEquals(1, root.path("total").asInt());
    }

    @Test
    public void searchJqlDefaultsMaxResultsWhenNonPositive() throws Exception {
        client.searchJql("x", 0);
        assertTrue(lastUri.get(), lastUri.get().endsWith("&maxResults=50"));
    }
}
