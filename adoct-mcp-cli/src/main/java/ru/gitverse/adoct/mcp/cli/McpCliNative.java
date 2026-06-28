package ru.gitverse.adoct.mcp.cli;

import ru.gitverse.adoct.mcp.tools.ToolRegistry;

/**
 * Точка входа для GraalVM native-image. В отличие от {@link McpCli}, использует
 * {@link ToolRegistry#coreTools()} — без asciidoctorj-зависимого {@code confluence_publish_adoc}
 * (JRuby несовместим с native-image). Поэтому этот класс задан как {@code mainClass} native-бинаря,
 * а полный JVM-вход — {@link McpCli}.
 */
public final class McpCliNative {

    private McpCliNative() {
    }

    public static void main(String[] args) throws Exception {
        McpCli.run(args, supplier -> new ToolRegistry(supplier).coreTools());
    }
}
