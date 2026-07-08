package io.github.adoct.mcp.tools;

import org.junit.Test;
import io.github.adoct.mcp.AtlassianEndpoint;
import io.github.adoct.mcp.AtlassianKind;
import io.github.adoct.mcp.EndpointSupplier;
import io.github.adoct.mcp.McpTool;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Тест фильтрации тулов по включённым группам ({@link EndpointSupplier#enabledToolGroups()}). */
public class ToolRegistryTest {

    @Test
    public void allGroupsByDefault() {
        List<String> names = names(List::of);

        assertTrue(names.stream().anyMatch(n -> n.startsWith("jira_")));
        assertTrue(names.stream().anyMatch(n -> n.startsWith("confluence_")));
        assertTrue(names.stream().anyMatch(n -> n.startsWith("bitbucket_")));
    }

    @Test
    public void onlyEnabledGroupsAreExposed() {
        EndpointSupplier jiraOnly = new EndpointSupplier() {
            @Override
            public List<AtlassianEndpoint> all() {
                return List.of();
            }

            @Override
            public Set<AtlassianKind> enabledToolGroups() {
                return EnumSet.of(AtlassianKind.JIRA);
            }
        };

        List<String> names = names(jiraOnly);

        assertTrue(names.stream().anyMatch(n -> n.startsWith("jira_")));
        assertFalse(names.stream().anyMatch(n -> n.startsWith("confluence_")));
        assertFalse(names.stream().anyMatch(n -> n.startsWith("bitbucket_")));
    }

    private static List<String> names(EndpointSupplier supplier) {
        return new ToolRegistry(supplier).tools().stream().map(McpTool::name).toList();
    }
}
