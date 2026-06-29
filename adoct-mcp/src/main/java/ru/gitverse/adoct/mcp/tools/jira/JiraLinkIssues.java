package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/**
 * {@code jira_link_issues} — создаёт внутреннюю связь между двумя задачами Jira
 * ({@code POST /rest/api/2/issueLink}). Имя типа резолвится по {@code /rest/api/2/issueLinkType}
 * (по {@code name}/{@code inward}/{@code outward}); при неизвестном типе — ошибка со списком доступных.
 */
public final class JiraLinkIssues implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("inwardIssue", "Ключ задачи на «входящей» стороне связи (напр. PLC-19)", true)
                .str("outwardIssue", "Ключ задачи на «исходящей» стороне связи (напр. PLC-6)", true)
                .str("type", "Имя типа связи как в Jira (напр. Requires, Blocks, Relates)", true)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_link_issues",
                "Связать две задачи Jira внутренней связью (тип резолвится по issueLinkType).",
                schema, args -> {
            String inward = c.reqStr(args, "inwardIssue");
            String outward = c.reqStr(args, "outwardIssue");
            String type = c.jira(args).linkIssues(inward, outward, c.reqStr(args, "type"));
            ObjectNode out = c.mapper().createObjectNode();
            out.put("linked", true);
            out.put("inwardIssue", inward);
            out.put("outwardIssue", outward);
            out.put("type", type);
            return c.ok(out);
        });
    }
}
