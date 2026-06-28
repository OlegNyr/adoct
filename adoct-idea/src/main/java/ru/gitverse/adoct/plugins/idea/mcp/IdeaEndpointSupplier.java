package ru.gitverse.adoct.plugins.idea.mcp;

import ru.gitverse.adoct.mcp.AtlassianEndpoint;
import ru.gitverse.adoct.mcp.EndpointSupplier;
import ru.gitverse.adoct.mcp.TeamMember;
import ru.gitverse.adoct.mcp.Template;
import ru.gitverse.adoct.plugins.idea.settings.ConfluenceSettingsService;

import java.util.List;
import java.util.Optional;

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

    @Override
    public Optional<String> defaultJiraProject() {
        return nonBlank(McpSettingsService.getInstance().getDefaultJiraProject());
    }

    @Override
    public Optional<String> defaultConfluenceSpace() {
        return nonBlank(McpSettingsService.getInstance().getDefaultConfluenceSpace());
    }

    @Override
    public List<TeamMember> team() {
        return McpSettingsService.getInstance().getTeam().stream()
                .filter(m -> m.username != null && !m.username.isBlank())
                .map(m -> new TeamMember(m.username, m.displayName, m.role))
                .toList();
    }

    @Override
    public List<Template> templates() {
        return McpSettingsService.getInstance().getTemplates().stream()
                .filter(t -> t.name != null && !t.name.isBlank())
                .map(t -> new Template(t.name, t.body))
                .toList();
    }

    @Override
    public String workflowDiagram() {
        return McpSettingsService.getInstance().getWorkflowDiagram();
    }

    private static Optional<String> nonBlank(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
