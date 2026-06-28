package ru.gitverse.adoct.mcp.cli;

import ru.gitverse.adoct.mcp.AtlassianEndpoint;
import ru.gitverse.adoct.mcp.EndpointSupplier;
import ru.gitverse.adoct.mcp.TeamMember;
import ru.gitverse.adoct.mcp.Template;

import java.util.List;
import java.util.Optional;

/** {@link EndpointSupplier} поверх {@link CliConfig} — отдаёт эндпоинты/дефолты/ростер/шаблоны из конфига CLI. */
final class ConfigEndpointSupplier implements EndpointSupplier {

    private final CliConfig config;

    ConfigEndpointSupplier(CliConfig config) {
        this.config = config;
    }

    @Override
    public List<AtlassianEndpoint> all() {
        return config.endpoints;
    }

    @Override
    public Optional<String> defaultJiraProject() {
        return nonBlank(config.defaultJiraProject);
    }

    @Override
    public Optional<String> defaultConfluenceSpace() {
        return nonBlank(config.defaultConfluenceSpace);
    }

    @Override
    public List<TeamMember> team() {
        return config.team;
    }

    @Override
    public List<Template> templates() {
        return config.templates;
    }

    @Override
    public String workflowDiagram() {
        return config.workflowDiagram;
    }

    private static Optional<String> nonBlank(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
