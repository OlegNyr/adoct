package ru.gitverse.adoct.mcp.tools.bitbucket;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.InputSchema;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.Tool;
import ru.gitverse.adoct.mcp.tools.ToolContext;

/** {@code bitbucket_list_projects} — список проектов Bitbucket. */
public final class BitbucketListProjects implements Tool {

    @Override
    public McpTool create(ToolContext c) {
        ObjectNode schema = InputSchema.object()
                .integer("limit", "Лимит (1..100, по умолчанию 25)", false)
                .integer("start", "Смещение для пагинации (по умолчанию 0)", false)
                .str("host", "Хост Bitbucket; иначе хост по умолчанию", false)
                .build();
        return new McpTool("bitbucket_list_projects", "Список проектов Bitbucket.", schema, args ->
                c.ok(c.bitbucket(args).listProjects(c.optInt(args, "start", 0), c.optInt(args, "limit", 25))));
    }
}
