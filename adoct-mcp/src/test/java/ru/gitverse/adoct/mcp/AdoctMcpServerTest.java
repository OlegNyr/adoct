package ru.gitverse.adoct.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** End-to-end проверка MCP-сервера через реальные HTTP-запросы; Jira-вызов идёт в локальный stub. */
public class AdoctMcpServerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private HttpServer jiraStub;
    private AdoctMcpServer mcp;
    private String mcpUrl;
    private final java.util.concurrent.atomic.AtomicReference<String> lastJiraBody =
            new java.util.concurrent.atomic.AtomicReference<>("");

    @Before
    public void setUp() throws IOException {
        jiraStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jiraStub.createContext("/", exchange -> {
            lastJiraBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            // совместимо и с поиском (total/issues), и с созданием (key)
            byte[] body = "{\"key\":\"DEF-1\",\"total\":1,\"issues\":[{\"key\":\"ABC-1\"}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        jiraStub.start();
        String host = "http://127.0.0.1:" + jiraStub.getAddress().getPort();

        EndpointSupplier supplier = new EndpointSupplier() {
            @Override
            public List<AtlassianEndpoint> all() {
                return List.of(new AtlassianEndpoint(host, "tok"));
            }

            @Override
            public java.util.Optional<String> defaultJiraProject() {
                return java.util.Optional.of("DEF");
            }

            @Override
            public List<ru.gitverse.adoct.mcp.TeamMember> team() {
                return List.of(new ru.gitverse.adoct.mcp.TeamMember("jdoe", "John Doe", "Backend"));
            }

            @Override
            public List<ru.gitverse.adoct.mcp.Template> templates() {
                return List.of(new ru.gitverse.adoct.mcp.Template("story", "As a … I want … so that …"));
            }
        };
        mcp = new AdoctMcpServer(supplier, "adoct", "test");
        mcp.start("127.0.0.1", 0);
        mcpUrl = "http://127.0.0.1:" + mcp.port() + "/mcp";
    }

    @After
    public void tearDown() {
        if (mcp != null) {
            mcp.close();
        }
        if (jiraStub != null) {
            jiraStub.stop(0);
        }
    }

    @Test
    public void initializeEchoesProtocolAndServerInfo() throws Exception {
        JsonNode r = rpc("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2024-11-05\"}}").body();
        assertEquals("2.0", r.path("jsonrpc").asText());
        assertEquals("2024-11-05", r.path("result").path("protocolVersion").asText());
        assertEquals("adoct", r.path("result").path("serverInfo").path("name").asText());
        assertTrue(r.path("result").path("capabilities").has("tools"));
    }

    @Test
    public void toolsListExposesAllTools() throws Exception {
        JsonNode tools = rpc("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}")
                .body().path("result").path("tools");
        assertEquals(74, tools.size());
        String names = tools.toString();
        assertTrue(names, names.contains("jira_list_team"));
        assertTrue(names, names.contains("jira_list_templates"));
        assertTrue(names, names.contains("jira_get_workflow"));
        assertTrue(names, names.contains("jira_assign_issue"));
        assertTrue(names, names.contains("jira_get_project_statuses"));
        assertTrue(names, names.contains("jira_list_assignable_users"));
        assertTrue(names, names.contains("jira_search"));
        assertTrue(names, names.contains("jira_transition_issue"));
        assertTrue(names, names.contains("jira_list_sprints"));
        assertTrue(names, names.contains("jira_get_board_backlog"));
        assertTrue(names, names.contains("jira_delete_issue"));
        assertTrue(names, names.contains("jira_add_worklog"));
        assertTrue(names, names.contains("jira_link_to_epic"));
        assertTrue(names, names.contains("jira_create_sprint"));
        assertTrue(names, names.contains("jira_download_attachments"));
        assertTrue(names, names.contains("confluence_get_page"));
        assertTrue(names, names.contains("confluence_search"));
        assertTrue(names, names.contains("confluence_list_spaces"));
        assertTrue(names, names.contains("confluence_get_default_space"));
        assertTrue(names, names.contains("confluence_export_tree_to_adoc"));
        assertTrue(names, names.contains("confluence_publish_adoc"));
        assertTrue(names, names.contains("confluence_move_page"));
        assertTrue(names, names.contains("confluence_get_page_diff"));
        assertTrue(names, names.contains("confluence_reply_to_comment"));
        assertTrue(names, names.contains("confluence_add_labels"));
        assertTrue(names, names.contains("confluence_download_attachment"));
        assertTrue(names, names.contains("bitbucket_search"));
        assertTrue(names, names.contains("bitbucket_list_projects"));
        assertTrue(names, names.contains("bitbucket_list_repositories"));
        assertTrue(names, names.contains("bitbucket_get_file"));
        assertTrue(names, names.contains("bitbucket_browse"));
        assertTrue(names, names.contains("bitbucket_list_pull_requests"));
        assertTrue(names, names.contains("bitbucket_get_pull_request_diff"));
    }

    @Test
    public void toolsCallJiraSearchHitsStub() throws Exception {
        JsonNode result = rpc("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"jira_search\",\"arguments\":{\"jql\":\"project = ABC\"}}}")
                .body().path("result");
        assertFalse(result.path("isError").asBoolean());
        assertTrue(result.path("content").get(0).path("text").asText().contains("ABC-1"));
    }

    @Test
    public void jiraCreateIssueUsesDefaultProjectWhenOmitted() throws Exception {
        JsonNode result = rpc("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"jira_create_issue\",\"arguments\":"
                + "{\"issueType\":\"Task\",\"summary\":\"hi\"}}}").body().path("result");
        assertFalse(result.path("isError").asBoolean());
        assertTrue(lastJiraBody.get(), lastJiraBody.get().contains("\"key\":\"DEF\""));
    }

    @Test
    public void jiraListTeamAndTemplatesRoundTrip() throws Exception {
        JsonNode team = rpc("{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"jira_list_team\"}}").body().path("result");
        assertFalse(team.path("isError").asBoolean());
        assertTrue(team.path("content").get(0).path("text").asText().contains("jdoe"));

        JsonNode templates = rpc("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"jira_list_templates\"}}").body().path("result");
        assertTrue(templates.path("content").get(0).path("text").asText().contains("story"));
    }

    @Test
    public void toolsCallUnknownToolIsError() throws Exception {
        JsonNode result = rpc("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"nope\"}}").body().path("result");
        assertTrue(result.path("isError").asBoolean());
    }

    @Test
    public void promptsListAndGetReturnPersona() throws Exception {
        JsonNode list = rpc("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"prompts/list\"}")
                .body().path("result").path("prompts");
        assertTrue(list.toString(), list.toString().contains("product_owner"));

        JsonNode result = rpc("{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"prompts/get\","
                + "\"params\":{\"name\":\"product_owner\"}}").body().path("result");
        String text = result.path("messages").get(0).path("content").path("text").asText();
        assertTrue(text, text.contains("продукт-овнер"));
    }

    @Test
    public void notificationGetsNoBody() throws Exception {
        HttpResponse<String> resp = post("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
        assertEquals(202, resp.statusCode());
        assertTrue(resp.body().isEmpty());
    }

    private record Rpc(JsonNode body) {
    }

    private Rpc rpc(String json) throws Exception {
        HttpResponse<String> resp = post(json);
        assertEquals(200, resp.statusCode());
        return new Rpc(mapper.readTree(resp.body()));
    }

    private HttpResponse<String> post(String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(mcpUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
