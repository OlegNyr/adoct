package ru.gitverse.adoct.anonymize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Анонимизация JSON-артефактов экспорта ({@code content.json}, {@code links.json}).
 *
 * <p>Обходит дерево Jackson, сохраняя структуру, и подменяет значения по имени поля.
 * HTML-блобы ({@code content}/{@code view}) прогоняются через {@link StorageHtmlAnonymizer},
 * чтобы они остались согласованы с {@code body.storage.html}/{@code view.storage.html}.
 * Ключи объекта {@code attachment} (имена файлов) переименовываются согласованно с
 * переименованием вложений на диске.
 */
public class JsonAnonymizer {

    private static final Set<String> HTML_FIELDS = Set.of("content", "view", "body", "value", "storage");
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Anonymizer anon;
    private final StorageHtmlAnonymizer html;

    public JsonAnonymizer(Anonymizer anon, StorageHtmlAnonymizer html) {
        this.anon = anon;
        this.html = html;
    }

    @SneakyThrows
    public String anonymizeJson(String json) {
        if (StringUtils.isBlank(json)) {
            return json;
        }
        JsonNode root = mapper.readTree(json);
        JsonNode result = process(root, null);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
    }

    private JsonNode process(JsonNode node, String key) {
        if (node.isObject()) {
            boolean renameKeys = "attachment".equals(key);
            ObjectNode result = NODES.objectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String name = field.getKey();
                String newName = renameKeys ? anon.fileName(name) : name;
                result.set(newName, processValue(name, field.getValue()));
            }
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = NODES.arrayNode();
            for (JsonNode item : node) {
                result.add(processValue(key, item));
            }
            return result;
        }
        return node;
    }

    private JsonNode processValue(String fieldName, JsonNode value) {
        if (value.isObject() || value.isArray()) {
            return process(value, fieldName);
        }
        if (value.isTextual()) {
            return NODES.textNode(transformText(fieldName, value.asText()));
        }
        return value;
    }

    private String transformText(String fieldName, String text) {
        if (StringUtils.isBlank(text)) {
            return text;
        }
        String field = fieldName == null ? "" : fieldName.toLowerCase(Locale.ROOT);
        if (HTML_FIELDS.contains(field) || looksLikeHtml(text)) {
            return html.anonymizeFragment(text);
        }
        return switch (field) {
            case "url", "href", "base", "self", "link", "webui", "tinyui", "download" -> anon.url(text);
            case "userkey", "accountid" -> anon.userKey(text);
            case "username" -> anon.login(text);
            case "displayname", "fullname" -> anon.name(text);
            case "title", "content-title", "name" -> anon.title(text);
            case "space", "spacekey", "space-key" -> anon.spaceKey(text);
            case "filename" -> anon.fileName(text);
            case "date", "datetime", "when", "created", "lastmodified", "friendlywhen" -> anon.date(text);
            default -> containsHost(text) ? anon.url(text) : text;
        };
    }

    private static boolean looksLikeHtml(String text) {
        return text.contains("</") || text.contains("<ac:") || text.contains("<p") || text.contains("<table");
    }

    private static boolean containsHost(String text) {
        return text.contains("http://") || text.contains("https://");
    }
}
