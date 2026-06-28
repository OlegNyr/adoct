package ru.gitverse.adoct.plugins.idea.mcp;

import com.intellij.ide.AppLifecycleListener;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Стартует встроенный MCP-сервер при запуске IDE. Слушатель приложения регистрируется в plugin.xml
 * ({@code <applicationListeners>}) и однократно дёргает {@link McpServerService#startOnce()}.
 */
public final class McpServerLauncher implements AppLifecycleListener {

    @Override
    public void appStarted() {
        McpServerService.getInstance().startOnce();
    }

    @Override
    public void appFrameCreated(@NotNull List<String> commandLineArgs) {
        // Резерв на случай, если appStarted уже произошёл к моменту загрузки плагина.
        McpServerService.getInstance().startOnce();
    }
}
