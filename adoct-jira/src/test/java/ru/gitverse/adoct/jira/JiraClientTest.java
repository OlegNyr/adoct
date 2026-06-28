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
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private String responseBody = "{}";
    private int responseStatus = 200;

    @Before
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastUri.set(exchange.getRequestURI().toString());
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastMethod.set(exchange.getRequestMethod());
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            if (responseStatus == 204 || body.length == 0) {
                exchange.sendResponseHeaders(responseStatus, -1);
            } else {
                exchange.sendResponseHeaders(responseStatus, body.length);
                exchange.getResponseBody().write(body);
            }
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

    @Test
    public void updateIssuePutsPayload() throws Exception {
        responseStatus = 204;
        com.fasterxml.jackson.databind.node.ObjectNode payload =
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        payload.putObject("fields").put("summary", "new");
        client.updateIssue("ABC-1", payload);

        assertEquals("PUT", lastMethod.get());
        assertEquals("/rest/api/2/issue/ABC-1", lastUri.get());
        assertTrue(lastBody.get(), lastBody.get().contains("\"summary\":\"new\""));
    }

    @Test
    public void transitionIssuePostsTransitionId() throws Exception {
        responseStatus = 204;
        client.transitionIssue("ABC-1", "31");

        assertEquals("POST", lastMethod.get());
        assertEquals("/rest/api/2/issue/ABC-1/transitions", lastUri.get());
        assertTrue(lastBody.get(), lastBody.get().contains("\"transition\""));
        assertTrue(lastBody.get(), lastBody.get().contains("\"id\":\"31\""));
    }

    @Test
    public void addCommentPostsBody() throws Exception {
        responseBody = "{\"id\":\"100\"}";
        client.addComment("ABC-1", "hello");

        assertEquals("POST", lastMethod.get());
        assertEquals("/rest/api/2/issue/ABC-1/comment", lastUri.get());
        assertTrue(lastBody.get(), lastBody.get().contains("\"body\":\"hello\""));
    }

    @Test
    public void getTransitionsReads() throws Exception {
        responseBody = "{\"transitions\":[{\"id\":\"31\",\"name\":\"Done\"}]}";
        assertEquals("31", client.getTransitions("ABC-1").path("transitions").get(0).path("id").asText());
        assertEquals("/rest/api/2/issue/ABC-1/transitions", lastUri.get());
    }

    @Test
    public void listBoardsFiltersByProject() throws Exception {
        responseBody = "{\"values\":[]}";
        client.listBoards("ABC");
        assertEquals("/rest/agile/1.0/board?projectKeyOrId=ABC", lastUri.get());
    }

    @Test
    public void listBoardsNoFilter() throws Exception {
        responseBody = "{\"values\":[]}";
        client.listBoards(null);
        assertEquals("/rest/agile/1.0/board", lastUri.get());
    }

    @Test
    public void getSprintIssuesClampsMaxResults() throws Exception {
        responseBody = "{\"issues\":[]}";
        client.getSprintIssues("5", 999);
        assertEquals("/rest/agile/1.0/sprint/5/issue?maxResults=100", lastUri.get());
    }

    @Test
    public void listSprintsPassesState() throws Exception {
        responseBody = "{\"values\":[]}";
        client.listSprints("7", "active,future");
        assertEquals("/rest/agile/1.0/board/7/sprint?state=active%2Cfuture", lastUri.get());
    }

    @Test
    public void deleteIssueSendsDelete() throws Exception {
        responseStatus = 204;
        client.deleteIssue("ABC-1", true);
        assertEquals("DELETE", lastMethod.get());
        assertEquals("/rest/api/2/issue/ABC-1?deleteSubtasks=true", lastUri.get());
    }

    @Test
    public void addWorklogPostsTimeSpent() throws Exception {
        responseBody = "{\"id\":\"1\"}";
        com.fasterxml.jackson.databind.node.ObjectNode p =
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        p.put("timeSpent", "2h");
        client.addWorklog("ABC-1", p);
        assertEquals("POST", lastMethod.get());
        assertEquals("/rest/api/2/issue/ABC-1/worklog", lastUri.get());
        assertTrue(lastBody.get(), lastBody.get().contains("\"timeSpent\":\"2h\""));
    }

    @Test
    public void addIssuesToSprintPostsIssues() throws Exception {
        responseStatus = 204;
        client.addIssuesToSprint("5", java.util.List.of("ABC-1", "ABC-2"));
        assertEquals("POST", lastMethod.get());
        assertEquals("/rest/agile/1.0/sprint/5/issue", lastUri.get());
        assertTrue(lastBody.get(), lastBody.get().contains("\"issues\":[\"ABC-1\",\"ABC-2\"]"));
    }

    @Test
    public void getChangelogExpandsChangelog() throws Exception {
        responseBody = "{\"changelog\":{}}";
        client.getChangelog("ABC-1");
        assertEquals("/rest/api/2/issue/ABC-1?expand=changelog", lastUri.get());
    }

    @Test
    public void removeWatcherEncodesUsername() throws Exception {
        responseStatus = 204;
        client.removeWatcher("ABC-1", "john doe");
        assertEquals("DELETE", lastMethod.get());
        assertEquals("/rest/api/2/issue/ABC-1/watchers?username=john+doe", lastUri.get());
    }

    @Test
    public void assignIssuePutsName() throws Exception {
        responseStatus = 204;
        client.assignIssue("ABC-1", "john");
        assertEquals("PUT", lastMethod.get());
        assertEquals("/rest/api/2/issue/ABC-1/assignee", lastUri.get());
        assertTrue(lastBody.get(), lastBody.get().contains("\"name\":\"john\""));
    }

    @Test
    public void getProjectStatusesReads() throws Exception {
        responseBody = "[]";
        client.getProjectStatuses("ABC");
        assertEquals("GET", lastMethod.get());
        assertEquals("/rest/api/2/project/ABC/statuses", lastUri.get());
    }

    @Test
    public void assignableUsersBuildsQuery() throws Exception {
        responseBody = "[]";
        client.assignableUsers("ABC", "jo hn", 500);
        String uri = lastUri.get();
        assertTrue(uri, uri.startsWith("/rest/api/2/user/assignable/search?project=ABC"));
        assertTrue(uri, uri.contains("&maxResults=100"));   // 500 зажат до 100
        assertTrue(uri, uri.contains("&query=jo+hn"));
    }

    @Test
    public void assignableUsersOmitsBlankQuery() throws Exception {
        responseBody = "[]";
        client.assignableUsers("ABC", null, 0);
        assertEquals("/rest/api/2/user/assignable/search?project=ABC&maxResults=50", lastUri.get());
    }
}
