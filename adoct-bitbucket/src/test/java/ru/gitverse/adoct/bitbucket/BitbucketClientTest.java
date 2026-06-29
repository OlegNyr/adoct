package ru.gitverse.adoct.bitbucket;

import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Юнит-тест {@link BitbucketClient} на локальном HTTP-стабе (без живого Bitbucket). */
public class BitbucketClientTest {

    private HttpServer server;
    private BitbucketClient client;
    private volatile String lastMethod;
    private volatile String lastUri;
    private volatile String lastBody;

    @Before
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastMethod = exchange.getRequestMethod();
            lastUri = exchange.getRequestURI().toString();
            lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] body = "{\"values\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        client = new BitbucketClient("http://127.0.0.1:" + server.getAddress().getPort(), "token");
    }

    @After
    public void tearDown() {
        server.stop(0);
    }

    @Test
    public void verifyToken_hitsProjectsAndReturnsStatus() throws Exception {
        int code = client.verifyToken();

        assertEquals("GET", lastMethod);
        assertEquals("/rest/api/1.0/projects?limit=1", lastUri);
        assertEquals(200, code);
    }

    @Test
    public void searchCode_postsToSearchEndpointWithCodeEntityAndFilters() throws Exception {
        client.searchCode("ДБО", "ABC", "app", 0, 25);

        assertEquals("POST", lastMethod);
        assertEquals("/rest/search/latest/search", lastUri);
        assertTrue(lastBody, lastBody.contains("\"entities\""));
        assertTrue(lastBody, lastBody.contains("\"code\""));
        // фильтры добавляются модификаторами запроса
        assertTrue(lastBody, lastBody.contains("project:ABC"));
        assertTrue(lastBody, lastBody.contains("repo:app"));
    }

    @Test
    public void listProjects_getsProjectsWithStartAndLimit() throws Exception {
        client.listProjects(10, 50);

        assertEquals("GET", lastMethod);
        assertEquals("/rest/api/1.0/projects?start=10&limit=50", lastUri);
    }

    @Test
    public void listRepos_withProjectKeyHitsProjectRepos() throws Exception {
        client.listRepos("ABC", 0, 25);

        assertEquals("GET", lastMethod);
        assertEquals("/rest/api/1.0/projects/ABC/repos?start=0&limit=25", lastUri);
    }

    @Test
    public void listRepos_withoutProjectKeyHitsAllRepos() throws Exception {
        client.listRepos(null, 0, 10);

        assertEquals("/rest/api/1.0/repos?start=0&limit=10", lastUri);
    }

    @Test
    public void browse_buildsBrowsePathWithRefAndPaging() throws Exception {
        client.browse("ABC", "app", "src/Main.java", "develop", 0, 100);

        assertEquals("GET", lastMethod);
        assertTrue(lastUri, lastUri.startsWith("/rest/api/1.0/projects/ABC/repos/app/browse/src/Main.java?"));
        assertTrue(lastUri, lastUri.contains("start=0"));
        assertTrue(lastUri, lastUri.contains("limit=100"));
        assertTrue(lastUri, lastUri.contains("at=develop"));
    }

    @Test
    public void listPullRequests_defaultsToOpenState() throws Exception {
        client.listPullRequests("ABC", "app", null, 0, 25);

        assertTrue(lastUri, lastUri.startsWith("/rest/api/1.0/projects/ABC/repos/app/pull-requests?"));
        assertTrue(lastUri, lastUri.contains("state=OPEN"));
        assertTrue(lastUri, lastUri.contains("start=0"));
    }

    @Test
    public void getPullRequestDiff_appendsContextLines() throws Exception {
        client.getPullRequestDiff("ABC", "app", 42, 5);

        assertEquals("/rest/api/1.0/projects/ABC/repos/app/pull-requests/42/diff?contextLines=5", lastUri);
    }

    @Test
    public void getPullRequestActivities_hitsActivitiesEndpoint() throws Exception {
        client.getPullRequestActivities("ABC", "app", 42, 0, 25);

        assertEquals("/rest/api/1.0/projects/ABC/repos/app/pull-requests/42/activities?start=0&limit=25", lastUri);
    }
}
