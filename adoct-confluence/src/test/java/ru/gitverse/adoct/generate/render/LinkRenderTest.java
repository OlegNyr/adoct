package ru.gitverse.adoct.generate.render;

import org.junit.Test;

/** Ссылки: внутренние перекрёстные (якоря Confluence) и внешние (остаются обычными a href). */
public class LinkRenderTest extends AbstractStorageRendererTest {

    @Test
    public void crossReferenceBecomesConfluenceLink() {
        String adoc = """
                [glossary]
                [[routing]]Маршрутизация::
                Текст.

                [[admin]]Администратор::
                Управляет <<routing,маршрутизацией>>.
                """;
        String xhtml = render(adoc).xhtml();
        // якорь-цель из [[routing]]
        assertContains(xhtml,
                "<ac:structured-macro ac:name=\"anchor\"><ac:parameter ac:name=\"\">routing</ac:parameter></ac:structured-macro>");
        // ссылка из <<routing,...>>
        assertContains(xhtml, "<ac:link ac:anchor=\"routing\"><ac:link-body>маршрутизацией</ac:link-body></ac:link>");
        // не должно остаться сырых внутренних <a>
        assertNotContains(xhtml, "href=\"#routing\"");
        assertNotContains(xhtml, "<a id=\"routing\">");
    }

    @Test
    public void externalLinkIsLeftUntouched() {
        String xhtml = render("ссылка https://example.com[сайт].\n").xhtml();
        assertContains(xhtml, "<a href=\"https://example.com\">сайт</a>");
        assertNotContains(xhtml, "<ac:link");
    }
}
