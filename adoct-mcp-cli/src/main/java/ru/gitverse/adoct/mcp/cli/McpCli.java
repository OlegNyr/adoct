package ru.gitverse.adoct.mcp.cli;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.gitverse.adoct.mcp.AdoctMcpServer;
import ru.gitverse.adoct.mcp.EndpointSupplier;
import ru.gitverse.adoct.mcp.McpDispatcher;
import ru.gitverse.adoct.mcp.McpTool;
import ru.gitverse.adoct.mcp.tools.ToolRegistry;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

/**
 * Запуск MCP-сервера из командной строки. По умолчанию транспорт — stdio (один JSON-RPC на строку),
 * с {@code --http} поднимается HTTP-сервер. Набор инструментов передаётся фабрикой, чтобы native-вход
 * ({@link McpCliNative}) мог исключить asciidoctorj-зависимые тулы.
 */
public final class McpCli {

    static final String NAME = "adoct-mcp";
    static final String VERSION = "0.1.0";

    private McpCli() {
    }

    /** JVM-вход: полный набор инструментов (включая confluence_publish_adoc). */
    public static void main(String[] args) throws Exception {
        run(args, supplier -> new ToolRegistry(supplier).tools());
    }

    /** Общий запуск: транспорт по конфигу, инструменты — из переданной фабрики. */
    static void run(String[] args, Function<EndpointSupplier, List<McpTool>> toolset) throws Exception {
        CliConfig config = CliConfig.load(args);
        List<McpTool> tools = toolset.apply(new ConfigEndpointSupplier(config));
        if (config.stdio) {
            runStdio(tools);
        } else {
            runHttp(tools, config);
        }
    }

    private static void runStdio(List<McpTool> tools) throws IOException {
        McpDispatcher dispatcher = new McpDispatcher(tools, NAME, VERSION);
        ObjectWriter writer = dispatcher.mapper().writer().without(SerializationFeature.INDENT_OUTPUT);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        Writer out = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            ObjectNode response;
            try {
                response = dispatcher.dispatch(dispatcher.mapper().readTree(line));
            } catch (Exception parse) {
                response = dispatcher.error(null, -32700, "Parse error");
            }
            if (response != null) {
                out.write(writer.writeValueAsString(response));
                out.write('\n');
                out.flush();
            }
        }
    }

    private static void runHttp(List<McpTool> tools, CliConfig config) throws Exception {
        AdoctMcpServer server = new AdoctMcpServer(tools, NAME, VERSION);
        server.start(config.bindHost, config.port);
        System.err.printf("MCP server on http://%s:%d/mcp%n", config.bindHost, server.port());
        CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            latch.countDown();
        }));
        latch.await();
    }
}
