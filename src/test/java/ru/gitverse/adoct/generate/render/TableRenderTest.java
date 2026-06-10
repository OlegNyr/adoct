package ru.gitverse.adoct.generate.render;

import org.junit.Test;

/** Таблицы: заголовок сверху (thead) и тело (tbody). */
public class TableRenderTest extends AbstractStorageRendererTest {

    @Test
    public void tableWithHeader() {
        String adoc = """
                [options="header"]
                |===
                | H1 | H2
                | a | b
                |===
                """;
        String xhtml = render(adoc).xhtml();
        assertContains(xhtml, "<thead><tr><th>H1</th><th>H2</th></tr></thead>");
        assertContains(xhtml, "<tbody><tr><td>a</td><td>b</td></tr></tbody>");
    }
}
