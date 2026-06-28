package ru.gitverse.adoct.plugins.idea.service;

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
    public void extractDisplayRefParsesSpaceAndTitle() {
        Optional<PublishDocsToConfluence.DisplayRef> ref =
                PublishDocsToConfluence.extractDisplayRef("http://localhost:8090/display/DDDD/dsfdsaf");
        assertTrue(ref.isPresent());
        assertEquals("DDDD", ref.get().spaceKey());
        assertEquals("dsfdsaf", ref.get().title());
    }

    @Test
    public void extractDisplayRefDecodesTitle() {
        Optional<PublishDocsToConfluence.DisplayRef> ref =
                PublishDocsToConfluence.extractDisplayRef("http://localhost:8090/display/DEV/My+Page+Title");
        assertTrue(ref.isPresent());
        assertEquals("My Page Title", ref.get().title());
    }

    @Test
    public void extractDisplayRefAbsentForViewpageUrl() {
        assertFalse(PublishDocsToConfluence.extractDisplayRef(
                "https://confluence.example.com/pages/viewpage.action?pageId=42").isPresent());
    }

    @Test
    public void resolveConfluencyIdKeepsPlainNumber() throws Exception {
        // числовой ID возвращается как есть, к серверу и файлу не обращаемся
        assertEquals("12345", PublishDocsToConfluence.resolveConfluencyId(null, null, " 12345 "));
    }

    @Test
    public void resolveConfluencyIdExtractsIdFromViewpageUrl() throws Exception {
        // URL с pageId резолвится без обращения к серверу (file == null → без перезаписи)
        assertEquals("42", PublishDocsToConfluence.resolveConfluencyId(
                null, null, "https://confluence.example.com/pages/viewpage.action?pageId=42"));
    }

    @Test
    public void resolveConfluencyIdNullForBlank() throws Exception {
        assertEquals(null, PublishDocsToConfluence.resolveConfluencyId(null, null, "  "));
        assertEquals(null, PublishDocsToConfluence.resolveConfluencyId(null, null, null));
    }

    @Test
    public void replaceConfluencyIdSwapsExistingValue() {
        String content = "= Заголовок\n:confluency-id: http://localhost:8090/display/DDDD/dsfdsaf\n\nтело\n";
        String result = PublishDocsToConfluence.replaceConfluencyId(content, "98765");
        assertEquals("= Заголовок\n:confluency-id: 98765\n\nтело\n", result);
    }

    @Test
    public void replaceConfluencyIdInsertsWhenMissing() {
        String content = "= Заголовок\n\nтело\n";
        String result = PublishDocsToConfluence.replaceConfluencyId(content, "555");
        assertEquals("= Заголовок\n:confluency-id: 555\n\nтело\n", result);
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
