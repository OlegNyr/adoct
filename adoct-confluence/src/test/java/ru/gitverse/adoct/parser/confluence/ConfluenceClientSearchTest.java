package ru.gitverse.adoct.parser.confluence;

import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Юнит-тест каскада поиска {@link ConfluenceClient} на локальном HTTP-стабе (без живого Confluence). */
public class ConfluenceClientSearchTest {

    private HttpServer server;
    private ConfluenceClient client;
    private final List<String> cqlQueries = new ArrayList<>();
    private volatile String titleHitFor;
    private volatile String textHitFor;

    @Before
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rest/api/content/search", exchange -> {
            String cql = query(exchange.getRequestURI().getRawQuery()).getOrDefault("cql", "");
            cqlQueries.add(cql);
            boolean hit = (titleHitFor != null && cql.contains(titleHitFor))
                    || (textHitFor != null && cql.contains(textHitFor));
            String body = hit
                    ? "{\"results\":[{\"title\":\"Hit\",\"space\":{\"key\":\"PLCHAT\"},"
                            + "\"_links\":{\"webui\":\"/display/PLCHAT/Hit\"}}]}"
                    : "{\"results\":[]}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        client = new ConfluenceClient("http://127.0.0.1:" + server.getAddress().getPort(), "token");
    }

    @After
    public void tearDown() {
        server.stop(0);
    }

    @Test
    public void searchText_fallsBackToTextWhenTitleMisses() {
        textHitFor = "text ~";

        List<ConfluenceClient.PageHit> result = client.searchText("ДБО", null);

        assertEquals(1, result.size());
        assertEquals("Hit", result.getFirst().title());
        assertEquals("PLCHAT", result.getFirst().space());
        // каскад: title =, title ~, text ~ — все три запроса, последний нашёл
        assertEquals(3, cqlQueries.size());
        assertTrue(cqlQueries.get(0).startsWith("title = "));
        assertTrue(cqlQueries.get(1).startsWith("title ~ "));
        assertTrue(cqlQueries.get(2).startsWith("text ~ "));
    }

    @Test
    public void searchText_withoutKeyDoesNotRestrictSpace() {
        textHitFor = "text ~";

        client.searchText("ДБО", null);

        // ни один запрос не должен содержать фильтр по пространству
        assertTrue(cqlQueries.stream().noneMatch(q -> q.contains("space.key")));
    }

    @Test
    public void searchText_withKeyRestrictsSpace() {
        titleHitFor = "title = ";

        client.searchText("Глоссарий", "PLCHAT");

        assertTrue(cqlQueries.getFirst().contains("space.key = \"PLCHAT\""));
    }

    @Test
    public void searchText_stopsAtExactTitle() {
        titleHitFor = "title = ";

        List<ConfluenceClient.PageHit> result = client.searchText("Глоссарий", null);

        assertEquals(1, result.size());
        assertEquals("один запрос — точное совпадение по заголовку", 1, cqlQueries.size());
        assertTrue(cqlQueries.getFirst().startsWith("title = "));
    }

    @Test
    public void search_titleOnly_neverQueriesText() {
        List<LinkResult> result = client.search("нет такого", "PLCHAT");

        assertTrue(result.isEmpty());
        // только заголовочный каскад, без text ~ (чтобы резолюция ссылок не хватала левые страницы)
        assertEquals(2, cqlQueries.size());
        assertTrue(cqlQueries.stream().noneMatch(q -> q.contains("text ~")));
        assertTrue(cqlQueries.getFirst().contains("space.key = \"PLCHAT\""));
    }

    private static Map<String, String> query(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        return java.util.Arrays.stream(raw.split("&"))
                .map(p -> p.split("=", 2))
                .collect(Collectors.toMap(
                        p -> URLDecoder.decode(p[0], StandardCharsets.UTF_8),
                        p -> p.length > 1 ? URLDecoder.decode(p[1], StandardCharsets.UTF_8) : ""));
    }
}
