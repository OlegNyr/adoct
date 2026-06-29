package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/**
 * {@code jira_list_templates} — шаблоны задач по типам (свободный текст). Ассистент читает шаблон
 * нужного типа и сам формирует вызов {@code jira_create_issue}; сервер шаблоны не интерпретирует.
 */
public final class JiraListTemplates implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object().build();
        return new McpTool("jira_list_templates",
                "Шаблоны задач по типам (issueType + текст). Используй текст шаблона нужного типа, "
                        + "чтобы собрать jira_create_issue.",
                schema, args -> {
            ArrayNode out = c.mapper().createArrayNode();
            c.templates().stream()
                    .filter(t -> t.issueType() != null && !t.issueType().isBlank())
                    .forEach(t -> {
                        ObjectNode n = out.addObject();
                        n.put("issueType", t.issueType());
                        n.put("template", t.body());
                    });
            return c.ok(out);
        });
    }
}
