package ru.gitverse.adoct.mcp.cli;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CliConfigTest {

    @Test
    public void jsonFileParsedAndArgsOverride() throws Exception {
        Path file = Files.createTempFile("mcpcfg", ".json");
        Files.writeString(file, """
                {
                  "bindHost": "0.0.0.0", "port": 7000, "transport": "http",
                  "defaultJiraProject": "ABC", "defaultConfluenceSpace": "DEV",
                  "endpoints": [{"host": "http://h", "token": "t"}],
                  "team": [{"username": "u", "displayName": "U", "role": "Dev"}],
                  "templates": [{"name": "Story", "body": "text"}],
                  "workflowDiagram": "@startuml"
                }""");
        try {
            CliConfig c = CliConfig.load(new String[] {"--config", file.toString(), "--port", "9999"});
            assertFalse("transport=http из json", c.stdio);
            assertEquals("аргумент перекрывает json", 9999, c.port);
            assertEquals("ABC", c.defaultJiraProject);
            assertEquals(1, c.endpoints.size());
            assertEquals("http://h", c.endpoints.get(0).host());
            assertEquals(1, c.team.size());
            assertEquals("u", c.team.get(0).username());
            assertEquals(1, c.templates.size());
            assertEquals("Story", c.templates.get(0).name());
            assertEquals("@startuml", c.workflowDiagram);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void defaultsAreStdioOnPort7337() throws Exception {
        CliConfig c = CliConfig.load(new String[] {});
        assertTrue(c.stdio);
        assertEquals(7337, c.port);
        assertTrue(c.endpoints.isEmpty());
    }
}
