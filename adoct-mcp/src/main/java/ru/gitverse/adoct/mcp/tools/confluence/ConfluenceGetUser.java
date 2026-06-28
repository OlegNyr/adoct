package ru.gitverse.adoct.mcp.tools.confluence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code confluence_get_user} — пользователь Confluence по ключу. */
public final class ConfluenceGetUser implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .str("userKey", "Ключ пользователя Confluence", true)
                .str("host", "Хост Confluence; иначе хост по умолчанию", false)
                .build();
        return new McpTool("confluence_get_user", "Получить пользователя Confluence по ключу.", schema, args ->
                c.ok(c.confluence(args).user(c.reqStr(args, "userKey"))));
    }
}
