package org.tools.asciidoc.plugins.idea.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PublishDocsToConfluenceTest {

    @Test
    public void insertConfluencyIdAfterTitle() {
        String content = "= Заголовок\n\nтело\n";
        String result = PublishDocsToConfluence.insertConfluencyId(content, "12345");
        assertEquals("= Заголовок\n:confluency-id: 12345\n\nтело\n", result);
    }

    @Test
    public void insertConfluencyIdWithoutTitleGoesToTop() {
        String content = "просто текст\n";
        String result = PublishDocsToConfluence.insertConfluencyId(content, "777");
        assertEquals(":confluency-id: 777\nпросто текст\n", result);
    }

    @Test
    public void extractPageIdFromFullUrl() {
        Optional<String> id = PublishDocsToConfluence.extractPageId(
                "https://confluence.example.com/pages/viewpage.action?pageId=21497584874");
        assertTrue(id.isPresent());
        assertEquals("21497584874", id.get());
    }

    @Test
    public void extractPageIdAbsentFromBareServerUrl() {
        assertFalse(PublishDocsToConfluence.extractPageId("https://confluence.example.com").isPresent());
    }

    @Test
    public void parseKeywordsSplitsTrimsAndDropsBlanks() {
        assertEquals(List.of("alpha", "beta", "gamma"),
                PublishDocsToConfluence.parseKeywords(" alpha, beta ,gamma , "));
    }

    @Test
    public void parseKeywordsEmptyWhenNullOrBlank() {
        assertTrue(PublishDocsToConfluence.parseKeywords(null).isEmpty());
        assertTrue(PublishDocsToConfluence.parseKeywords("  ").isEmpty());
    }

    @Test
    public void sha256MatchesKnownVector() {
        // SHA-256("abc") — стандартный тест-вектор
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                PublishDocsToConfluence.sha256("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void folderTreeCollectsAncestorsTopDown() {
        Path dir = Path.of("root");
        List<Path> files = List.of(
                Path.of("root", "a.adoc"),
                Path.of("root", "sub", "b.adoc"),
                Path.of("root", "sub", "deep", "c.adoc"));
        List<Path> folders = PublishDocsToConfluence.folderTree(dir, files);
        assertEquals(List.of(
                Path.of("root"),
                Path.of("root", "sub"),
                Path.of("root", "sub", "deep")), folders);
    }
}
