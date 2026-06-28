package ru.gitverse.adoct.plugins.idea.mcp;

import ru.gitverse.adoct.mcp.AtlassianEndpoint;
import ru.gitverse.adoct.mcp.EndpointSupplier;
import ru.gitverse.adoct.plugins.idea.settings.ConfluenceSettingsService;

import java.util.List;

/**
 * Поставляет точки подключения MCP из настроек плагина ({@link ConfluenceSettingsService}). Таблица
 * хранит произвольные {@code host + token}, поэтому хост Jira добавляется туда же отдельной строкой —
 * тулы выбирают нужный по аргументу {@code host}.
 */
public final class IdeaEndpointSupplier implements EndpointSupplier {

    @Override
    public List<AtlassianEndpoint> all() {
        return ConfluenceSettingsService.getInstance().getServers().stream()
                .filter(e -> e.getHost() != null && !e.getHost().isBlank())
                .map(e -> new AtlassianEndpoint(e.getHost(), e.getToken()))
                .toList();
    }
}
