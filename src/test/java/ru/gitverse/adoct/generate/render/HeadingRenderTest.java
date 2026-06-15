package ru.gitverse.adoct.generate.render;

import org.junit.Test;

/** Заголовки: уровни h1..h6, инлайн-форматирование, якорь секции и выравнивание по роли. */
public class HeadingRenderTest extends AbstractStorageRendererTest {

    @Test
    public void heading() {
        String xhtml = render("== Заголовок\n").xhtml();
        // в заголовке теперь есть якорь секции перед текстом
        assertContains(xhtml, "<ac:structured-macro ac:name=\"anchor\">");
        assertContains(xhtml, "Заголовок</h1>");
    }

    @Test
    public void headingInlineFormatting() {
        String xhtml = render("== Реализация в `plchat` и *жирный*\n").xhtml();
        assertContains(xhtml, "Реализация в <code>plchat</code> и <strong>жирный</strong></h1>");
        // не должно остаться экранированных тегов
        assertNotContains(xhtml, "&lt;code&gt;");
    }

    @Test
    public void nestedHeadingLevels() {
        String xhtml = render("== A\n\n=== B\n").xhtml();
        assertContains(xhtml, "A</h1>");
        assertContains(xhtml, "B</h2>");
    }

    @Test
    public void explicitSectionAnchorIsEmitted() {
        String xhtml = render("[#routing]\n== Маршрутизация\n").xhtml();
        assertContains(xhtml,
                "<ac:structured-macro ac:name=\"anchor\"><ac:parameter ac:name=\"\">routing</ac:parameter></ac:structured-macro>");
    }

    @Test
    public void sectionAlignmentRoleBecomesStyle() {
        String xhtml = render("[.text-center]\n== По центру\n").xhtml();
        assertContains(xhtml, "<h1 style=\"text-align: center;\">");
    }
}
