package ru.gitverse.adoct.mcp;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Минимальный билдер JSON Schema (object) для описания входа тула. */
public final class InputSchema {

    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    private final ObjectNode schema = F.objectNode();
    private final ObjectNode properties = F.objectNode();
    private final ArrayNode required = F.arrayNode();

    private InputSchema() {
        schema.put("type", "object");
        schema.set("properties", properties);
    }

    public static InputSchema object() {
        return new InputSchema();
    }

    public InputSchema str(String name, String description, boolean req) {
        return add(name, "string", description, req);
    }

    public InputSchema bool(String name, String description, boolean req) {
        return add(name, "boolean", description, req);
    }

    public InputSchema integer(String name, String description, boolean req) {
        return add(name, "integer", description, req);
    }

    private InputSchema add(String name, String type, String description, boolean req) {
        ObjectNode p = properties.putObject(name);
        p.put("type", type);
        if (description != null) {
            p.put("description", description);
        }
        if (req) {
            required.add(name);
        }
        return this;
    }

    public ObjectNode build() {
        if (!required.isEmpty()) {
            schema.set("required", required);
        }
        return schema;
    }
}
