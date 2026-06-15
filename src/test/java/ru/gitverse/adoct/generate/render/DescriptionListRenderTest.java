package ru.gitverse.adoct.generate.render;

import org.junit.Test;

/** Списки определений (dl/dt/dd): простые, с вложенной таблицей и с жёстким переносом. */
public class DescriptionListRenderTest extends AbstractStorageRendererTest {

    @Test
    public void descriptionList() {
        String adoc = """
                [glossary]
                Термин::
                Описание термина.
                """;
        String xhtml = render(adoc).xhtml();
        assertContains(xhtml, "<dl>");
        assertContains(xhtml, "<dt>Термин</dt>");
        assertContains(xhtml, "<dd>Описание термина.</dd>");
        assertContains(xhtml, "</dl>");
    }

    @Test
    public void descriptionListWithNestedTable() {
        String adoc = """
                [glossary]
                Термин::
                Описание.
                +
                [options="header"]
                |===
                | H1 | H2
                | a | b
                |===
                """;
        String xhtml = render(adoc).xhtml();
        assertContains(xhtml, "<dd>Описание.");
        assertContains(xhtml, "<thead><tr><th>H1</th><th>H2</th></tr></thead>");
        assertContains(xhtml, "<tbody><tr><td>a</td><td>b</td></tr></tbody>");
    }

    @Test
    public void descriptionListWithHardBreakIsWellFormed() {
        String adoc = """
                [glossary]
                Термин::
                Первая строка. +
                Вторая строка.
                """;
        String xhtml = render(adoc).xhtml();
        assertContains(xhtml, "<br />");
        // не должно остаться незакрытого <br> перед </dd>
        assertNotContains(xhtml, "<br>");
    }
}
