package ru.gitverse.adoct.plugins.idea.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import ru.gitverse.adoct.mcp.AdoctMcpServer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Жизненный цикл встроенного MCP-сервера. App-level сервис: поднимает {@link AdoctMcpServer} один раз
 * (в фоне, не на EDT) и закрывает его при выгрузке плагина / завершении IDE через {@link Disposable}.
 *
 * <p>Хост/порт берутся из {@link McpSettingsService}. {@link #isRunning()} — наличие объекта,
 * {@link #pingHttp()} — реальный живой health-check по HTTP; {@link #restart()} перезапускает сервер.
 */
@Service(Service.Level.APP)
public final class McpServerService implements Disposable {

    private static final Logger LOG = Logger.getInstance(McpServerService.class);
    private static final String SERVER_NAME = "AsciiDocTools MCP";
    private static final String SERVER_VERSION = "1.0";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile AdoctMcpServer server;
    private volatile String lastError;

    public static McpServerService getInstance() {
        return ApplicationManager.getApplication().getService(McpServerService.class);
    }

    /** Поднят ли сервер (успешно стартовал и не закрыт) — по наличию объекта, без сетевой проверки. */
    public boolean isRunning() {
        return server != null;
    }

    /** Последняя ошибка старта (или {@code null}, если последний старт успешен). */
    public String lastError() {
        return lastError;
    }

    /**
     * Живой health-check: реальный JSON-RPC {@code ping} по HTTP на текущий адрес (с коротким таймаутом).
     * Отвечает на вопрос «реально ли сервер отвечает», в отличие от {@link #isRunning()}.
     */
    public boolean pingHttp() {
        try {
            HttpResponse<Void> response = newClient().send(rpc("ping"), HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /** Тянет имена тулов с живого сервера ({@code tools/list}). Бросает, если сервер не отвечает. */
    public List<String> fetchToolNames() throws IOException, InterruptedException {
        HttpResponse<String> response = newClient().send(rpc("tools/list"),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }
        List<String> names = new ArrayList<>();
        for (JsonNode tool : MAPPER.readTree(response.body()).path("result").path("tools")) {
            names.add(tool.path("name").asText());
        }
        return names;
    }

    /** Базовый URL к локальному серверу (0.0.0.0 → 127.0.0.1). */
    private static String localUrl() {
        McpSettingsService s = McpSettingsService.getInstance();
        String host = s.getBindHost();
        if (host == null || host.isBlank() || "0.0.0.0".equals(host)) {
            host = "127.0.0.1";
        }
        return "http://" + host + ":" + s.getPort() + "/mcp";
    }

    private static HttpClient newClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofMillis(800)).build();
    }

    private static HttpRequest rpc(String method) {
        return HttpRequest.newBuilder(URI.create(localUrl()))
                .timeout(Duration.ofMillis(1500))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\"}"))
                .build();
    }

    /** Адрес MCP-эндпоинта по текущим настройкам (для копирования). */
    public static String endpointUrl() {
        McpSettingsService s = McpSettingsService.getInstance();
        return "http://" + s.getBindHost() + ":" + s.getPort() + "/mcp";
    }

    /** Идемпотентный запуск сервера в фоновом потоке (если включён в настройках). */
    public void startOnce() {
        McpSettingsService settings = McpSettingsService.getInstance();
        if (!settings.isEnabled()) {
            LOG.info("AsciiDocTools MCP server disabled in settings — not starting");
            return;
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        String host = settings.getBindHost();
        int port = settings.getPort();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                AdoctMcpServer mcp = new AdoctMcpServer(new IdeaEndpointSupplier(), SERVER_NAME, SERVER_VERSION);
                mcp.start(host, port);
                server = mcp;
                lastError = null;
                LOG.info("AsciiDocTools MCP server started on http://" + host + ":" + port + "/mcp");
            } catch (Exception e) {
                started.set(false);
                lastError = e.getMessage() != null ? e.getMessage() : e.toString();
                LOG.warn("AsciiDocTools MCP server failed to start on " + host + ":" + port, e);
            }
        });
    }

    /** Перезапуск с актуальными настройками (после изменения порта/хоста/включения). */
    public synchronized void restart() {
        AdoctMcpServer mcp = server;
        if (mcp != null) {
            mcp.close();
            server = null;
        }
        started.set(false);
        startOnce();
    }

    @Override
    public void dispose() {
        AdoctMcpServer mcp = server;
        if (mcp != null) {
            mcp.close();
            server = null;
        }
    }
}
