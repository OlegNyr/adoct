package ru.gitverse.adoct.generate.render;

import org.junit.Test;

/** Прочие блоки: example→tip/expand, quote→blockquote, sidebar→panel, colist→нумерованный список. */
public class BlockRenderTest extends AbstractStorageRendererTest {

    @Test
    public void exampleBecomesTip() {
        String xhtml = render("[example]\n====\nпример\n====\n").xhtml();
        assertContains(xhtml, "<ac:structured-macro ac:name=\"tip\">");
        assertContains(xhtml, "пример");
    }

    @Test
    public void collapsibleExampleBecomesExpand() {
        String xhtml = render(".Детали\n[%collapsible]\n====\nскрыто\n====\n").xhtml();
        assertContains(xhtml, "<ac:structured-macro ac:name=\"expand\">");
        assertContains(xhtml, "<ac:parameter ac:name=\"title\">Детали</ac:parameter>");
    }

    @Test
    public void quoteBecomesBlockquote() {
        String xhtml = render("[quote,Автор,Источник]\n____\nцитата\n____\n").xhtml();
        assertContains(xhtml, "<blockquote>");
        assertContains(xhtml, "цитата");
        assertContains(xhtml, "— Автор, Источник");
    }

    @Test
    public void sidebarBecomesPanel() {
        String xhtml = render("****\nна полях\n****\n").xhtml();
        assertContains(xhtml, "<ac:structured-macro ac:name=\"panel\">");
        assertContains(xhtml, "на полях");
    }

    @Test
    public void thematicBreakBecomesHr() {
        String xhtml = render("первый\n\n'''\n\nвторой\n").xhtml();
        assertContains(xhtml, "<hr/>");
    }

    @Test
    public void floatingTitleBecomesHeading() {
        String xhtml = render("[discrete]\n== Дискретный\n").xhtml();
        assertContains(xhtml, "Дискретный</h1>");
        // у дискретного заголовка нет якоря секции
        assertNotContains(xhtml, "<h1><ac:structured-macro ac:name=\"anchor\">");
    }

    @Test
    public void verseBecomesPreWithAttribution() {
        String xhtml = render("[verse,Поэт]\n____\nстрока раз\nстрока два\n____\n").xhtml();
        assertContains(xhtml, "<pre>");
        assertContains(xhtml, "строка раз");
        assertContains(xhtml, "— Поэт");
    }

    @Test
    public void calloutListBecomesOrderedList() {
        String adoc = """
                [source,java]
                ----
                int x = 1; // <1>
                ----
                <1> объявление переменной
                """;
        String xhtml = render(adoc).xhtml();
        assertContains(xhtml, "<ol><li>объявление переменной</li></ol>");
    }
}
