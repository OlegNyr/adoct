package ru.gitverse.adoct.generate.render;

import org.junit.Test;

/** Инлайн-форматирование, жёсткие переносы и починка невалидного XHTML (jsoup). */
public class InlineFormattingRenderTest extends AbstractStorageRendererTest {

    @Test
    public void paragraphInlineFormatting() {
        String xhtml = render("текст *жирный* конец\n").xhtml();
        assertContains(xhtml, "<p>");
        assertContains(xhtml, "<strong>жирный</strong>");
    }

    @Test
    public void hardLineBreakIsSelfClosed() {
        // Жёсткий перенос ` +` AsciiDoctor отдаёт как <br>; для строгого XML нужен самозакрытый тег.
        String xhtml = render("первая строка +\nвторая строка\n").xhtml();
        assertContains(xhtml, "<br />");
        assertNotContains(xhtml, "<br>");
    }

    @Test
    public void overlappingInlineTagsAreRepaired() throws Exception {
        // [.role]##*x  *## с пробелом перед закрывающей * заставляет AsciiDoctor выдать
        // перекрывающиеся теги <span><strong>..</span>..</strong>; jsoup должен их перебалансировать
        // так, чтобы фрагмент стал строго валидным XML (иначе Confluence отвергает страницу).
        String xhtml = render("[.note]##*Примечание  *##обычный текст *ncc.buffer-size*.\n").xhtml();
        assertContains(xhtml, "<span");
        assertWellFormedXml(xhtml);
    }
}
