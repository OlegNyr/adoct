package ru.gitverse.adoct.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;

/**
 * Живой смоук MCP-сервера против реального Confluence. Активен только при {@code -Dmcp.host}/{@code -Dmcp.token};
 * иначе пропускается. Прогоняет полный путь: HTTP → tools/call → parser ConfluenceClient → Confluence.
 *
 * <pre>
 * ./gradlew :adoct-mcp:test --tests "*McpLiveIT" \
 *   -Dmcp.host=http://localhost:8090 -Dmcp.token=XXXX -Dmcp.pageId=98361
 * </pre>
 */
public class McpLiveIT {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    public void getsConfluencePageOverMcp() throws Exception {
        String host = System.getProperty("mcp.host");
        String token = System.getProperty("mcp.token");
        assumeNotNull("нет mcp.host/mcp.token — стенд недоступен", host, token);
        String pageId = System.getProperty("mcp.pageId", "98361");

        EndpointSupplier supplier = () -> List.of(new AtlassianEndpoint(host, token));
        try (AdoctMcpServer mcp = new AdoctMcpServer(supplier, "live", "1")) {
            mcp.start("127.0.0.1", 0);
            String url = "http://127.0.0.1:" + mcp.port() + "/mcp";

            String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                    + "\"params\":{\"name\":\"confluence_get_page\",\"arguments\":{\"pageId\":\"" + pageId + "\"}}}";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode result = mapper.readTree(resp.body()).path("result");

            assertFalse("tool error: " + result, result.path("isError").asBoolean());
            String text = result.path("content").get(0).path("text").asText();
            assertTrue("ожидали поле title в ответе: " + text, text.contains("\"title\""));
            System.out.println("MCP confluence_get_page OK -> " + text.substring(0, Math.min(200, text.length())));
        }
    }
}
