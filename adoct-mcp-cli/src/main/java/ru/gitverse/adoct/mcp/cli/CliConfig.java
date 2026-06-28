package ru.gitverse.adoct.mcp.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.gitverse.adoct.mcp.AtlassianEndpoint;
import ru.gitverse.adoct.mcp.TeamMember;
import ru.gitverse.adoct.mcp.Template;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Конфигурация CLI MCP-сервера. Источники (по возрастанию приоритета): JSON-файл {@code --config},
 * переменные окружения {@code MCP_*}, аргументы командной строки. JSON разбирается деревом (без
 * рефлексии — дружелюбно к native-image).
 */
final class CliConfig {

    String bindHost = "127.0.0.1";
    int port = 7337;
    /** Транспорт: stdio (по умолчанию) либо HTTP. */
    boolean stdio = true;
    String defaultJiraProject = "";
    String defaultConfluenceSpace = "";
    final List<AtlassianEndpoint> endpoints = new ArrayList<>();
    final List<TeamMember> team = new ArrayList<>();
    final List<Template> templates = new ArrayList<>();
    String workflowDiagram = "";

    static CliConfig load(String[] args) throws Exception {
        CliConfig c = new CliConfig();
        Map<String, String> env = System.getenv();
        String configPath = firstNonBlank(argValue(args, "--config"), env.get("MCP_CONFIG"));
        if (configPath != null && !configPath.isBlank()) {
            c.applyJson(new ObjectMapper().readTree(Files.readString(Path.of(configPath))));
        }
        c.applyEnv(env);
        c.applyArgs(args);
        return c;
    }

    private void applyJson(JsonNode root) {
        bindHost = text(root, "bindHost", bindHost);
        port = root.path("port").asInt(port);
        String transport = root.path("transport").asText(null);
        if (transport != null) {
            stdio = !"http".equalsIgnoreCase(transport);
        }
        defaultJiraProject = text(root, "defaultJiraProject", defaultJiraProject);
        defaultConfluenceSpace = text(root, "defaultConfluenceSpace", defaultConfluenceSpace);
        workflowDiagram = text(root, "workflowDiagram", workflowDiagram);

        for (JsonNode e : root.path("endpoints")) {
            String host = e.path("host").asText("");
            if (!host.isBlank()) {
                endpoints.add(new AtlassianEndpoint(host, e.path("token").asText("")));
            }
        }
        for (JsonNode m : root.path("team")) {
            String username = m.path("username").asText("");
            if (!username.isBlank()) {
                team.add(new TeamMember(username, m.path("displayName").asText(""), m.path("role").asText("")));
            }
        }
        for (JsonNode t : root.path("templates")) {
            String name = t.path("name").asText("");
            if (!name.isBlank()) {
                templates.add(new Template(name, t.path("body").asText("")));
            }
        }
    }

    private void applyEnv(Map<String, String> env) {
        bindHost = firstNonBlank(env.get("MCP_BIND"), bindHost);
        String envPort = env.get("MCP_PORT");
        if (envPort != null && !envPort.isBlank()) {
            port = Integer.parseInt(envPort.trim());
        }
        String transport = env.get("MCP_TRANSPORT");
        if (transport != null) {
            stdio = !"http".equalsIgnoreCase(transport);
        }
        defaultJiraProject = firstNonBlank(env.get("MCP_JIRA_PROJECT"), defaultJiraProject);
        defaultConfluenceSpace = firstNonBlank(env.get("MCP_CONFLUENCE_SPACE"), defaultConfluenceSpace);
        // Быстрый одно-эндпоинтный режим из окружения.
        String host = env.get("MCP_HOST");
        if (host != null && !host.isBlank()) {
            endpoints.clear();
            endpoints.add(new AtlassianEndpoint(host, env.getOrDefault("MCP_TOKEN", "")));
        }
    }

    private void applyArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--http" -> stdio = false;
                case "--stdio" -> stdio = true;
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--bind" -> bindHost = args[++i];
                case "--config" -> i++; // уже учтён
                default -> { /* игнорируем неизвестные */ }
            }
        }
    }

    private static String text(JsonNode root, String field, String fallback) {
        String v = root.path(field).asText(null);
        return v == null ? fallback : v;
    }

    private static String argValue(String[] args, String name) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(name)) {
                return args[i + 1];
            }
        }
        return null;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
