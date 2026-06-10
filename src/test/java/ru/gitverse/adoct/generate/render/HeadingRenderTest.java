package ru.gitverse.adoct.generate.render;

import org.junit.Test;

/** Заголовки: уровни h1..h6 и инлайн-форматирование в тексте заголовка. */
public class HeadingRenderTest extends AbstractStorageRendererTest {

    @Test
    public void heading() {
        String xhtml = render("== Заголовок\n").xhtml();
        assertContains(xhtml, "<h1>Заголовок</h1>");
    }

    @Test
    public void headingInlineFormatting() {
        String xhtml = render("== Реализация в `plchat` и *жирный*\n").xhtml();
        assertContains(xhtml, "<h1>Реализация в <code>plchat</code> и <strong>жирный</strong></h1>");
        // не должно остаться экранированных тегов
        assertNotContains(xhtml, "&lt;code&gt;");
    }

    @Test
    public void nestedHeadingLevels() {
        String xhtml = render("== A\n\n=== B\n").xhtml();
        assertContains(xhtml, "<h1>A</h1>");
        assertContains(xhtml, "<h2>B</h2>");
    }
}
