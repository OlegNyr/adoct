package io.github.adoct.generate;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Чистые помощники {@link AdocPublisher} (без сети): резолв id, запись {@code :confluency-id:}, метки, хэш, дерево. */
public class AdocPublisherTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void inferPublishRootWalksUpIndexAdocChain() throws Exception {
        // doc/index.adoc, doc/03-architecture/index.adoc, doc/03-architecture/dbo-draft/index.adoc,
        // но kcenter (родитель doc) — без index.adoc → корень = doc.
        Path kcenter = tmp.getRoot().toPath();
        Path doc = kcenter.resolve("doc");
        Path arch = doc.resolve("03-architecture");
        Path dbo = arch.resolve("dbo-draft");
        Path integration = dbo.resolve("integration");
        Files.createDirectories(integration);
        for (Path folder : List.of(doc, arch, dbo)) {
            Files.writeString(folder.resolve("index.adoc"), "= Узел\n", StandardCharsets.UTF_8);
        }
        // integration без index.adoc, но подъём идёт по родителям, а не по самой папке файла
        assertEquals(doc, AdocPublisher.inferPublishRoot(integration));
    }

    @Test
    public void inferPublishRootStaysWhenNoParentIndex() throws Exception {
        Path dir = tmp.getRoot().toPath().resolve("solo");
        Files.createDirectories(dir);
        // у родителя dir нет index.adoc → корень = сама папка файла
        assertEquals(dir, AdocPublisher.inferPublishRoot(dir));
        assertNull(AdocPublisher.inferPublishRoot(null));
    }

    @Test
    public void readConfluencyIdReadsHeaderValue() throws Exception {
        Path file = tmp.getRoot().toPath().resolve("page.adoc");
        Files.writeString(file, "= Заголовок\n:confluency-id: 12345\n\nтело\n", StandardCharsets.UTF_8);
        assertEquals("12345", AdocPublisher.readConfluencyId(file));
    }

    @Test
    public void readConfluencyIdNullWhenAbsent() throws Exception {
        Path file = tmp.getRoot().toPath().resolve("page.adoc");
        Files.writeString(file, "= Заголовок\n\nтело\n", StandardCharsets.UTF_8);
        assertNull(AdocPublisher.readConfluencyId(file));
        assertNull(AdocPublisher.readConfluencyId(tmp.getRoot().toPath().resolve("missing.adoc")));
    }

    @Test
    public void insertConfluencyIdAfterTitle() {
        String content = "= Заголовок\n\nтело\n";
        assertEquals("= Заголовок\n:confluency-id: 12345\n\nтело\n",
                AdocPublisher.insertConfluencyId(content, "12345"));
    }

    @Test
    public void insertConfluencyIdWithoutTitleGoesToTop() {
        assertEquals(":confluency-id: 777\nпросто текст\n",
                AdocPublisher.insertConfluencyId("просто текст\n", "777"));
    }

    @Test
    public void replaceConfluencyIdSwapsExistingValue() {
        String content = "= Заголовок\n:confluency-id: http://localhost:8090/display/DDDD/dsfdsaf\n\nтело\n";
        assertEquals("= Заголовок\n:confluency-id: 98765\n\nтело\n",
                AdocPublisher.replaceConfluencyId(content, "98765"));
    }

    @Test
    public void replaceConfluencyIdInsertsWhenMissing() {
        assertEquals("= Заголовок\n:confluency-id: 555\n\nтело\n",
                AdocPublisher.replaceConfluencyId("= Заголовок\n\nтело\n", "555"));
    }

    @Test
    public void extractPageIdFromFullUrl() {
        Optional<String> id = AdocPublisher.extractPageId(
                "https://confluence.example.com/pages/viewpage.action?pageId=21497584874");
        assertTrue(id.isPresent());
        assertEquals("21497584874", id.get());
    }

    @Test
    public void extractPageIdAbsentFromBareServerUrl() {
        assertFalse(AdocPublisher.extractPageId("https://confluence.example.com").isPresent());
    }

    @Test
    public void extractDisplayRefParsesSpaceAndTitle() {
        Optional<AdocPublisher.DisplayRef> ref =
                AdocPublisher.extractDisplayRef("http://localhost:8090/display/DDDD/dsfdsaf");
        assertTrue(ref.isPresent());
        assertEquals("DDDD", ref.get().spaceKey());
        assertEquals("dsfdsaf", ref.get().title());
    }

    @Test
    public void extractDisplayRefDecodesTitle() {
        Optional<AdocPublisher.DisplayRef> ref =
                AdocPublisher.extractDisplayRef("http://localhost:8090/display/DEV/My+Page+Title");
        assertTrue(ref.isPresent());
        assertEquals("My Page Title", ref.get().title());
    }

    @Test
    public void extractDisplayRefAbsentForViewpageUrl() {
        assertFalse(AdocPublisher.extractDisplayRef(
                "https://confluence.example.com/pages/viewpage.action?pageId=42").isPresent());
    }

    @Test
    public void resolveConfluencyIdKeepsPlainNumber() throws Exception {
        assertEquals("12345", new AdocPublisher(null).resolveConfluencyId(null, " 12345 "));
    }

    @Test
    public void resolveConfluencyIdExtractsIdFromViewpageUrl() throws Exception {
        assertEquals("42", new AdocPublisher(null).resolveConfluencyId(
                null, "https://confluence.example.com/pages/viewpage.action?pageId=42"));
    }

    @Test
    public void resolveConfluencyIdNullForBlank() throws Exception {
        assertNull(new AdocPublisher(null).resolveConfluencyId(null, "  "));
        assertNull(new AdocPublisher(null).resolveConfluencyId(null, null));
    }

    @Test
    public void parseKeywordsSplitsTrimsAndDropsBlanks() {
        assertEquals(List.of("alpha", "beta", "gamma"),
                AdocPublisher.parseKeywords(" alpha, beta ,gamma , "));
    }

    @Test
    public void parseKeywordsEmptyWhenNullOrBlank() {
        assertTrue(AdocPublisher.parseKeywords(null).isEmpty());
        assertTrue(AdocPublisher.parseKeywords("  ").isEmpty());
    }

    @Test
    public void sha256MatchesKnownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                AdocPublisher.sha256("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void folderTreeCollectsAncestorsTopDown() {
        Path dir = Path.of("root");
        List<Path> files = List.of(
                Path.of("root", "a.adoc"),
                Path.of("root", "sub", "b.adoc"),
                Path.of("root", "sub", "deep", "c.adoc"));
        assertEquals(List.of(
                Path.of("root"),
                Path.of("root", "sub"),
                Path.of("root", "sub", "deep")), AdocPublisher.folderTree(dir, files));
    }
}
