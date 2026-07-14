package io.github.adoct.generate.render;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import io.github.adoct.generate.asciidoc.AsciiDocParser;
import io.github.adoct.generate.model.RenderResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Ссылки на другие {@code .adoc}-файлы → макрос ссылки на страницу Confluence ({@code ac:link}/{@code ri:page}
 * по заголовку файла-цели). Проверяем формы, которые AsciiDoctor рендерит с доп.атрибутами
 * ({@code link:x[]} → {@code class="bare"}, {@code link:x[T,role=..]} → {@code class}) — они должны
 * распознаваться так же, как {@code link:x[Текст]}.
 */
public class CrossDocLinkRenderTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** Рендерит main.adoc; рядом кладёт целевые файлы, чтобы заголовки страниц читались из них. */
    private RenderResult renderWithNeighbors(String mainBody) throws Exception {
        Path dir = tmp.getRoot().toPath();
        Files.writeString(dir.resolve("index.adoc"), "= Узел раздела\n\nТекст.\n", StandardCharsets.UTF_8);
        Files.createDirectories(dir.resolve("parent"));
        Files.writeString(dir.resolve("parent").resolve("index.adoc"),
                "= Родительский узел\n\nТекст.\n", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("child.adoc"), "= Дочерняя страница\n\nТекст.\n", StandardCharsets.UTF_8);
        Path main = dir.resolve("main.adoc");
        Files.writeString(main, "= Главная\n\n" + mainBody + "\n", StandardCharsets.UTF_8);
        try (AsciiDocParser parser = new AsciiDocParser()) {
            return new StorageRenderer("plantuml", dir, "").render(parser.parse(main));
        }
    }

    @Test
    public void explicitTextLinkToAdocBecomesPageLink() throws Exception {
        String xhtml = renderWithNeighbors("См. link:child.adoc[дочернюю страницу].").xhtml();
        assertContains(xhtml, "<ri:page ri:content-title=\"Дочерняя страница\"");
        assertContains(xhtml, "<ac:link-body>дочернюю страницу</ac:link-body>");
        assertNoRawAdocHref(xhtml);
    }

    @Test
    public void bareLinkToIndexUsesPageTitleAsText() throws Exception {
        // link:index.adoc[] -> <a href="index.adoc" class="bare">index.adoc</a>
        String xhtml = renderWithNeighbors("Наверх: link:index.adoc[].").xhtml();
        assertContains(xhtml, "<ri:page ri:content-title=\"Узел раздела\"");
        // текст ссылки — заголовок страницы, а не сырой путь index.adoc
        assertContains(xhtml, "<ac:link-body>Узел раздела</ac:link-body>");
        assertNotContains(xhtml, "index.adoc");
        assertNoRawAdocHref(xhtml);
    }

    @Test
    public void bareLinkToParentIndexBecomesPageLink() throws Exception {
        // link:parent/index.adoc[] -> <a href="parent/index.adoc" class="bare">parent/index.adoc</a>
        String xhtml = renderWithNeighbors("Родитель: link:parent/index.adoc[].").xhtml();
        assertContains(xhtml, "<ri:page ri:content-title=\"Родительский узел\"");
        assertContains(xhtml, "<ac:link-body>Родительский узел</ac:link-body>");
        assertNoRawAdocHref(xhtml);
    }

    @Test
    public void roledLinkToAdocBecomesPageLink() throws Exception {
        // link:child.adoc[Дочерняя,role=foo] -> <a href="child.adoc" class="foo">Дочерняя</a>
        String xhtml = renderWithNeighbors("link:child.adoc[Дочерняя,role=foo]").xhtml();
        assertContains(xhtml, "<ri:page ri:content-title=\"Дочерняя страница\"");
        assertContains(xhtml, "<ac:link-body>Дочерняя</ac:link-body>");
        assertNoRawAdocHref(xhtml);
    }

    @Test
    public void crossReferenceWithAnchorBecomesPageLinkWithAnchor() throws Exception {
        // <<child.adoc#sec,Раздел>> -> <a href="child.adoc#sec">Раздел</a> (outfilesuffix=.adoc в парсере)
        String xhtml = renderWithNeighbors("См. <<child.adoc#sec,Раздел>>.").xhtml();
        assertContains(xhtml, "<ac:link ac:anchor=\"sec\">");
        assertContains(xhtml, "<ri:page ri:content-title=\"Дочерняя страница\"");
        assertContains(xhtml, "<ac:link-body>Раздел</ac:link-body>");
        assertNoRawAdocHref(xhtml);
    }

    private static void assertNoRawAdocHref(String xhtml) {
        assertFalse(xhtml, xhtml.contains("<a href=\""));
    }

    private static void assertContains(String xhtml, String fragment) {
        assertTrue(xhtml, xhtml.contains(fragment));
    }

    private static void assertNotContains(String xhtml, String fragment) {
        assertFalse(xhtml, xhtml.contains(fragment));
    }
}
