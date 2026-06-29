package ru.gitverse.adoct.mcp.tools.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.jira.JiraClient;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

import java.util.List;

/**
 * {@code jira_create_issue} — создаёт задачу Jira со всеми полями за один вызов. Базовые поля
 * (project/issuetype/summary/description/labels/components/fixVersions/priority/произвольные fields)
 * пишутся в payload создания. Реляционные ({@code assignee}, {@code reporter}, {@code epicKey},
 * {@code links}) проставляются после создания «как сможем»: при неудаче задача всё равно создаётся,
 * а в ответе — список предупреждений по каждому неуспешному полю.
 */
public final class JiraCreateIssue implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("projectKey", "Ключ проекта (по умолчанию из настроек)", false)
                .str("issueType", "Тип задачи (например Task, Story, Bug, SDLC Task Lite)", true)
                .str("summary", "Заголовок", true)
                .str("description", "Описание (необязательно)", false)
                .str("assignee", "Логин исполнителя (необязательно)", false)
                .str("reporter", "Логин автора (необязательно)", false)
                .arr("labels", "Метки (массив строк)", false)
                .str("epicKey", "Epic Link — ключ эпика (необязательно)", false)
                .arr("links", "Связи: [{type, issue, direction: outward|inward}] (направление по умолчанию outward)",
                        false)
                .obj("fields", "Произвольные поля, в т.ч. кастомные (напр. customfield_10027)", false)
                .obj("customFields", "Синоним fields — произвольные/кастомные поля", false)
                .arr("components", "Компоненты (массив имён)", false)
                .arr("fixVersions", "Версии исправления (массив имён)", false)
                .str("priority", "Приоритет (имя, необязательно)", false)
                .str("host", "Хост Jira; иначе хост по умолчанию", false)
                .build();
        return new McpTool("jira_create_issue",
                "Создать задачу Jira со всеми полями за один вызов (assignee/labels/epic/links/кастомные поля).",
                schema, args -> {
            JiraClient jira = c.jira(args);
            ObjectMapper m = c.mapper();

            ObjectNode payload = m.createObjectNode();
            ObjectNode fields = payload.putObject("fields");
            fields.putObject("project").put("key", c.requireProject(args));
            fields.putObject("issuetype").put("name", c.reqStr(args, "issueType"));
            fields.put("summary", c.reqStr(args, "summary"));
            c.putIfPresent(fields, "description", c.text(args, "description"));
            putStringArray(fields, "labels", args.get("labels"));
            putNamedArray(fields, "components", args.get("components"));
            putNamedArray(fields, "fixVersions", args.get("fixVersions"));
            String priority = c.text(args, "priority");
            if (priority != null && !priority.isBlank()) {
                fields.putObject("priority").put("name", priority);
            }
            mergeRaw(fields, args.get("fields"));
            mergeRaw(fields, args.get("customFields"));

            String key = jira.createIssue(payload);

            ArrayNode warnings = m.createArrayNode();
            String assignee = c.text(args, "assignee");
            if (assignee != null && !assignee.isBlank()) {
                try {
                    jira.assignIssue(key, assignee);
                } catch (Exception e) {
                    warnings.add("assignee '" + assignee + "': " + message(e));
                }
            }
            String reporter = c.text(args, "reporter");
            if (reporter != null && !reporter.isBlank()) {
                try {
                    ObjectNode up = m.createObjectNode();
                    up.putObject("fields").putObject("reporter").put("name", reporter);
                    jira.updateIssue(key, up);
                } catch (Exception e) {
                    warnings.add("reporter '" + reporter + "': " + message(e));
                }
            }
            String epicKey = c.text(args, "epicKey");
            if (epicKey != null && !epicKey.isBlank()) {
                try {
                    jira.linkToEpic(epicKey, List.of(key));
                } catch (Exception e) {
                    warnings.add("epicKey '" + epicKey + "': " + message(e));
                }
            }
            JsonNode links = args.get("links");
            if (links != null && links.isArray()) {
                for (JsonNode link : links) {
                    String type = link.path("type").asText(null);
                    String issue = link.path("issue").asText(null);
                    if (type == null || issue == null) {
                        warnings.add("link: нужны поля type и issue");
                        continue;
                    }
                    boolean inward = "inward".equalsIgnoreCase(link.path("direction").asText("outward"));
                    String inwardKey = inward ? issue : key;
                    String outwardKey = inward ? key : issue;
                    try {
                        jira.linkIssues(inwardKey, outwardKey, type);
                    } catch (Exception e) {
                        warnings.add("link " + type + " " + issue + ": " + message(e));
                    }
                }
            }

            ObjectNode out = m.createObjectNode();
            out.put("key", key);
            out.set("warnings", warnings);
            return c.ok(out);
        });
    }

    private static void putStringArray(ObjectNode fields, String name, JsonNode source) {
        if (source != null && source.isArray()) {
            ArrayNode arr = fields.putArray(name);
            source.forEach(v -> arr.add(v.asText()));
        }
    }

    private static void putNamedArray(ObjectNode fields, String name, JsonNode source) {
        if (source != null && source.isArray()) {
            ArrayNode arr = fields.putArray(name);
            source.forEach(v -> arr.addObject().put("name", v.asText()));
        }
    }

    private static void mergeRaw(ObjectNode fields, JsonNode raw) {
        if (raw != null && raw.isObject()) {
            raw.fields().forEachRemaining(e -> fields.set(e.getKey(), e.getValue()));
        }
    }

    private static String message(Throwable e) {
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }
}
