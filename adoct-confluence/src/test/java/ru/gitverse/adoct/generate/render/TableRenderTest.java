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

    @Test
    public void columnSpan() {
        String adoc = """
                [cols="2*"]
                |===
                |a |b
                2+|объединённая
                |===
                """;
        String xhtml = render(adoc).xhtml();
        assertContains(xhtml, "<td colspan=\"2\">объединённая</td>");
    }

    @Test
    public void rowSpan() {
        String adoc = """
                [cols="2*"]
                |===
                .2+|сверху-вниз
                |a

                |b
                |===
                """;
        String xhtml = render(adoc).xhtml();
        assertContains(xhtml, "<td rowspan=\"2\">сверху-вниз</td>");
    }

    @Test
    public void asciidocCellRendersNestedBlocks() {
        String adoc = """
                [cols="1"]
                |===
                |просто текст
                a|* пункт один
                * пункт два
                |===
                """;
        String xhtml = render(adoc).xhtml();
        // rich-ячейка a| рендерит вложенный список, а не плоский текст
        assertContains(xhtml, "<ul><li>пункт один</li><li>пункт два</li></ul>");
    }

    @Test
    public void nestedTableInsideAsciidocCell() {
        String adoc = """
                [cols="1"]
                |===
                a|
                !===
                ! Вложенная ! Ячейка
                !===
                |===
                """;
        String xhtml = render(adoc).xhtml();
        // внешняя ячейка содержит вложенную таблицу
        assertContains(xhtml, "<td><table>");
        assertContains(xhtml, "Вложенная");
        assertContains(xhtml, "Ячейка");
    }

    @Test
    public void footerAndCaptionAndWidth() {
        String adoc = """
                .Подпись
                [%header%footer,cols="1",width="50%"]
                |===
                |H
                |тело
                |подвал
                |===
                """;
        String xhtml = render(adoc).xhtml();
        assertContains(xhtml, "<table style=\"width: 50%;\">");
        assertContains(xhtml, "<caption>Подпись</caption>");
        assertContains(xhtml, "<tfoot><tr><td>подвал</td></tr></tfoot>");
    }
}
