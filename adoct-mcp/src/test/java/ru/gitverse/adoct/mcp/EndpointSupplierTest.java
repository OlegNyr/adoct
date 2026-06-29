package ru.gitverse.adoct.mcp;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EndpointSupplierTest {

    private final AtlassianEndpoint conf = new AtlassianEndpoint("https://confluence.example.com", "c");
    private final AtlassianEndpoint jira = new AtlassianEndpoint("https://jira.example.com/", "j");
    private final EndpointSupplier supplier = () -> List.of(conf, jira);

    @Test
    public void authorityStripsSchemeAndPath() {
        assertEquals("confluence.example.com", EndpointSupplier.authority("https://confluence.example.com/rest"));
        assertEquals("jira.example.com", EndpointSupplier.authority("JIRA.example.com/"));
    }

    @Test
    public void forHostMatchesIgnoringSchemeAndCase() {
        assertEquals(jira, supplier.forHost("jira.example.com").orElseThrow());
        assertEquals(conf, supplier.forHost("https://CONFLUENCE.example.com").orElseThrow());
    }

    @Test
    public void blankHostFallsBackToDefault() {
        assertEquals(conf, supplier.forHost(null).orElseThrow());
        assertEquals(conf, supplier.forHost("  ").orElseThrow());
    }

    @Test
    public void unknownHostIsEmpty() {
        assertFalse(supplier.forHost("nope.example.com").isPresent());
    }

    @Test
    public void emptySupplierHasNoDefault() {
        EndpointSupplier empty = List::of;
        assertTrue(empty.defaultEndpoint().isEmpty());
    }

    @Test
    public void defaultEndpointByKindRoutesToMatchingService() {
        // baseline бага #2: jira-вызов не должен уходить на первый (Confluence) хост
        assertEquals(jira, supplier.defaultEndpoint(AtlassianKind.JIRA).orElseThrow());
        assertEquals(conf, supplier.defaultEndpoint(AtlassianKind.CONFLUENCE).orElseThrow());
    }

    @Test
    public void defaultEndpointPrefersPrimaryOfKind() {
        AtlassianEndpoint jira1 = new AtlassianEndpoint("https://j1", "a", AtlassianKind.JIRA, false);
        AtlassianEndpoint jira2 = new AtlassianEndpoint("https://j2", "b", AtlassianKind.JIRA, true);
        EndpointSupplier twoJira = () -> List.of(jira1, jira2);

        assertEquals(jira2, twoJira.defaultEndpoint(AtlassianKind.JIRA).orElseThrow());
    }

    @Test
    public void defaultEndpointFallsBackToFirstWhenKindAbsent() {
        // одно-хостовая инсталляция (только Confluence) — jira-вызов всё же получает хоть что-то
        EndpointSupplier onlyConf = () -> List.of(conf);
        assertEquals(conf, onlyConf.defaultEndpoint(AtlassianKind.JIRA).orElseThrow());
    }
}
