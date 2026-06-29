package ru.gitverse.adoct.mcp;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Юнит-тест эвристики типа {@link AtlassianKind#detect} и разбора {@link AtlassianKind#parse}. */
public class AtlassianKindTest {

    @Test
    public void detectByHostname() {
        assertEquals(AtlassianKind.JIRA, AtlassianKind.detect("https://jira.example.com"));
        assertEquals(AtlassianKind.CONFLUENCE, AtlassianKind.detect("https://confluence.example.com"));
        assertEquals(AtlassianKind.CONFLUENCE, AtlassianKind.detect("https://wiki.example.com"));
        assertEquals(AtlassianKind.BITBUCKET, AtlassianKind.detect("https://bitbucket.example.com"));
        assertEquals(AtlassianKind.BITBUCKET, AtlassianKind.detect("https://stash.example.com"));
    }

    @Test
    public void detectUnknownDefaultsToConfluence() {
        assertEquals(AtlassianKind.CONFLUENCE, AtlassianKind.detect("https://docs.example.com"));
        assertEquals(AtlassianKind.CONFLUENCE, AtlassianKind.detect(null));
    }

    @Test
    public void parseIsCaseInsensitiveWithFallback() {
        assertEquals(AtlassianKind.JIRA, AtlassianKind.parse("jira", AtlassianKind.CONFLUENCE));
        assertEquals(AtlassianKind.BITBUCKET, AtlassianKind.parse("  Bitbucket ", AtlassianKind.CONFLUENCE));
        assertEquals(AtlassianKind.CONFLUENCE, AtlassianKind.parse("", AtlassianKind.CONFLUENCE));
        assertEquals(AtlassianKind.JIRA, AtlassianKind.parse("garbage", AtlassianKind.JIRA));
    }
}
