package ru.gitverse.adoct.generate.render;

import org.junit.Test;

/** Admonitions → панели Confluence (с remap имён: note→info, caution/warning→note, important→warning). */
public class AdmonitionRenderTest extends AbstractStorageRendererTest {

    @Test
    public void simpleNoteBecomesInfoPanel() {
        String xhtml = render("NOTE: будь внимателен\n").xhtml();
        assertContains(xhtml, "<ac:structured-macro ac:name=\"info\">");
        assertContains(xhtml, "<ac:rich-text-body>");
        assertContains(xhtml, "будь внимателен");
    }

    @Test
    public void tipBecomesTipPanel() {
        String xhtml = render("TIP: совет\n").xhtml();
        assertContains(xhtml, "<ac:structured-macro ac:name=\"tip\">");
    }

    @Test
    public void warningBecomesNotePanel() {
        String xhtml = render("[WARNING]\n====\nопасно\n====\n").xhtml();
        assertContains(xhtml, "<ac:structured-macro ac:name=\"note\">");
        assertContains(xhtml, "опасно");
    }

    @Test
    public void cautionBecomesNotePanel() {
        String xhtml = render("CAUTION: осторожно\n").xhtml();
        assertContains(xhtml, "<ac:structured-macro ac:name=\"note\">");
    }

    @Test
    public void importantBecomesWarningPanel() {
        String xhtml = render("[IMPORTANT]\n====\nкритично\n====\n").xhtml();
        assertContains(xhtml, "<ac:structured-macro ac:name=\"warning\">");
        assertContains(xhtml, "критично");
    }

    @Test
    public void admonitionTitleBecomesParameter() {
        String xhtml = render(".Заголовок\n[NOTE]\n====\nтекст\n====\n").xhtml();
        assertContains(xhtml, "<ac:parameter ac:name=\"title\">Заголовок</ac:parameter>");
    }
}
