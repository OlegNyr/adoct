package ru.gitverse.adoct.generate.confluence;

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

/** Проверяет построение запросов новых методов publish-клиента на локальном stub-сервере (без сети). */
public class ConfluenceClientWriteTest {

    private HttpServer server;
    private ConfluenceClient client;
    private final AtomicReference<String> uri = new AtomicReference<>();
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private String responseBody = "{\"id\":\"99\"}";
    private int status = 200;

    @Before
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            uri.set(exchange.getRequestURI().toString());
            method.set(exchange.getRequestMethod());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] b = responseBody.getBytes(StandardCharsets.UTF_8);
            if (status == 204) {
                exchange.sendResponseHeaders(204, -1);
            } else {
                exchange.sendResponseHeaders(status, b.length);
                exchange.getResponseBody().write(b);
            }
            exchange.close();
        });
        server.start();
        client = new ConfluenceClient("http://127.0.0.1:" + server.getAddress().getPort(), "tok");
    }

    @After
    public void tearDown() {
        server.stop(0);
    }

    @Test
    public void deletePageSendsDelete() throws Exception {
        status = 204;
        client.deletePage("123");
        assertEquals("DELETE", method.get());
        assertEquals("/rest/api/content/123", uri.get());
    }

    @Test
    public void movePagePutsToMoveEndpoint() throws Exception {
        client.movePage("1", "append", "2");
        assertEquals("PUT", method.get());
        assertEquals("/rest/api/content/1/move/append/2", uri.get());
    }

    @Test
    public void addCommentPostsContainerAndBody() throws Exception {
        String id = client.addComment("55", null, "<p>hi</p>");
        assertEquals("99", id);
        assertEquals("POST", method.get());
        assertEquals("/rest/api/content", uri.get());
        assertTrue(body.get(), body.get().contains("\"type\":\"comment\""));
        assertTrue(body.get(), body.get().contains("\"id\":\"55\""));
    }

    @Test
    public void replyAddsAncestor() throws Exception {
        client.addComment("55", "70", "<p>re</p>");
        assertTrue(body.get(), body.get().contains("\"ancestors\""));
        assertTrue(body.get(), body.get().contains("\"id\":\"70\""));
    }

    @Test
    public void getVersionsReads() throws Exception {
        responseBody = "{\"results\":[]}";
        client.getVersions("9");
        assertEquals("GET", method.get());
        assertEquals("/rest/api/content/9/version", uri.get());
    }

    @Test
    public void deleteLabelEncodesName() throws Exception {
        status = 204;
        client.deleteLabel("9", "needs review");
        assertEquals("DELETE", method.get());
        assertEquals("/rest/api/content/9/label?name=needs+review", uri.get());
    }
}
