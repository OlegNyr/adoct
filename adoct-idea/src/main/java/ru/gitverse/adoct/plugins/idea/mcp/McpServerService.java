package ru.gitverse.adoct.plugins.idea.mcp;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import ru.gitverse.adoct.mcp.AdoctMcpServer;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Жизненный цикл встроенного MCP-сервера. App-level сервис: поднимает {@link AdoctMcpServer} один раз
 * (в фоне, не на EDT) и закрывает его при выгрузке плагина / завершении IDE через {@link Disposable}.
 *
 * <p>Порт/хост пока зашиты ({@code 127.0.0.1:7337}) — вынос в настройки запланирован вторым этапом.
 */
@Service(Service.Level.APP)
public final class McpServerService implements Disposable {

    private static final Logger LOG = Logger.getInstance(McpServerService.class);
    private static final String BIND_HOST = "127.0.0.1";
    private static final int PORT = 7337;
    private static final String SERVER_NAME = "AsciiDocTools MCP";
    private static final String SERVER_VERSION = "1.0";

    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile AdoctMcpServer server;

    public static McpServerService getInstance() {
        return ApplicationManager.getApplication().getService(McpServerService.class);
    }

    /** Идемпотентный запуск сервера в фоновом потоке. */
    public void startOnce() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                AdoctMcpServer mcp = new AdoctMcpServer(new IdeaEndpointSupplier(), SERVER_NAME, SERVER_VERSION);
                mcp.start(BIND_HOST, PORT);
                server = mcp;
                LOG.info("AsciiDocTools MCP server started on http://" + BIND_HOST + ":" + PORT + "/mcp");
            } catch (Exception e) {
                started.set(false);
                LOG.warn("AsciiDocTools MCP server failed to start on port " + PORT, e);
            }
        });
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
