package ru.gitverse.adoct.generate.render;

import org.junit.Test;

/** Списки: маркированные (ul) и нумерованные (ol). */
public class ListRenderTest extends AbstractStorageRendererTest {

    @Test
    public void unorderedList() {
        String xhtml = render("* one\n* two\n").xhtml();
        assertContains(xhtml, "<ul><li>one</li><li>two</li></ul>");
    }

    @Test
    public void orderedList() {
        String xhtml = render(". one\n. two\n").xhtml();
        assertContains(xhtml, "<ol><li>one</li><li>two</li></ol>");
    }
}
