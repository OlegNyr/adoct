package io.github.adoct.plugins.idea.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.adoct.mcp.AtlassianKind;
import io.github.adoct.plugins.idea.mcp.McpSettingsService.TeamMemberState;
import io.github.adoct.plugins.idea.mcp.McpSettingsService.TemplateState;
import io.github.adoct.plugins.idea.settings.ConfluenceSettingsService;

import java.util.Locale;

/**
 * Собирает из настроек плагина ({@link ConfluenceSettingsService} + {@link McpSettingsService}) JSON
 * в формате конфига CLI MCP-сервера ({@code CliConfig}) — чтобы запускать standalone-сервер
 * ({@code java -jar adoct-mcp.jar --config …}) с теми же эндпоинтами/дефолтами, что и встроенный.
 */
public final class McpConfigExporter {

    private McpConfigExporter() {
    }

    /**
     * @param includeTokens {@code true} — реальные токены в файле; {@code false} — плейсхолдеры
     *                      {@code ${MCP_TOKEN_N}} (значения берутся из переменных окружения).
     */
    public static String toJson(boolean includeTokens) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        McpSettingsService s = McpSettingsService.getInstance();

        root.put("transport", "http");
        root.put("bindHost", s.getBindHost());
        root.put("port", s.getPort());
        putIfNotBlank(root, "defaultJiraProject", s.getDefaultJiraProject());
        putIfNotBlank(root, "defaultConfluenceSpace", s.getDefaultConfluenceSpace());

        ArrayNode endpoints = root.putArray("endpoints");
        int index = 1;
        for (ConfluenceSettingsService.ServerEntry e : ConfluenceSettingsService.getInstance().getServers()) {
            if (e.getHost() == null || e.getHost().isBlank()) {
                continue;
            }
            ObjectNode ep = endpoints.addObject();
            ep.put("host", e.getHost());
            ep.put("kind", AtlassianKind.parse(e.getType(), AtlassianKind.detect(e.getHost()))
                    .name().toLowerCase(Locale.ROOT));
            ep.put("token", includeTokens ? nz(e.getToken()) : "${MCP_TOKEN_" + index + "}");
            if (e.isPrimary()) {
                ep.put("default", true);
            }
            index++;
        }

        ArrayNode team = mapper.createArrayNode();
        for (TeamMemberState m : s.getTeam()) {
            if (m.username == null || m.username.isBlank()) {
                continue;
            }
            ObjectNode node = team.addObject();
            node.put("username", m.username);
            node.put("displayName", nz(m.displayName));
            node.put("role", nz(m.role));
        }
        if (!team.isEmpty()) {
            root.set("team", team);
        }

        ArrayNode templates = mapper.createArrayNode();
        for (TemplateState t : s.getTemplates()) {
            if (t.issueType == null || t.issueType.isBlank()) {
                continue;
            }
            ObjectNode node = templates.addObject();
            node.put("issueType", t.issueType);
            node.put("body", nz(t.body));
            node.put("workflow", nz(t.workflow));
        }
        if (!templates.isEmpty()) {
            root.set("templates", templates);
        }

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    private static void putIfNotBlank(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
