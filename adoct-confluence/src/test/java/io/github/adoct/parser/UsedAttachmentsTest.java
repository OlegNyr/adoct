package io.github.adoct.parser;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link ConvertStorageToAdoc#usedAttachments()} — какие вложения тело страницы реально использует.
 * Набор нужен, чтобы не качать всё подряд, поэтому важны обе стороны: не потерять нужное и не
 * притащить лишнее.
 */
public class UsedAttachmentsTest {

    private static Set<String> used(String storage) {
        return new ConvertStorageToAdoc(storage, "").usedAttachments();
    }

    @Test
    public void picksUpImageAttachment() {
        Set<String> used = used("<p><ac:image><ri:attachment ri:filename=\"схема.png\"/></ac:image></p>");

        assertEquals(Set.of("схема.png"), used);
    }

    @Test
    public void picksUpAttachmentLink() {
        Set<String> used = used("<p><ac:link><ri:attachment ri:filename=\"договор.pdf\"/></ac:link></p>");

        assertEquals(Set.of("договор.pdf"), used);
    }

    @Test
    public void picksUpAllDrawioCandidates() {
        // DrawioRenderer ищет исходник среди пяти файлов — в набор должны попасть все
        Set<String> used = used("<ac:structured-macro ac:name=\"drawio\">"
                + "<ac:parameter ac:name=\"diagramName\">Архитектура</ac:parameter></ac:structured-macro>");

        assertEquals(Set.of("Архитектура", "Архитектура.png", "Архитектура.svg",
                "Архитектура.drawio.png", "Архитектура.drawio.svg"), used);
    }

    @Test
    public void picksUpAttachmentOfMacroOutsideRegistry() {
        // view-file в реестре макросов нет, но ссылка на файл в выводе остаётся — вложение нужно
        Set<String> used = used("<ac:structured-macro ac:name=\"view-file\">"
                + "<ac:parameter ac:name=\"name\"><ri:attachment ri:filename=\"презентация.pptx\"/>"
                + "</ac:parameter></ac:structured-macro>");

        assertTrue(used.toString(), used.contains("презентация.pptx"));
    }

    @Test
    public void picksUpPlainImgFileName() {
        Set<String> used = used("<p><img src=\"attache/вставка.png?version=2\"/></p>");

        assertEquals(Set.of("вставка.png"), used);
    }

    @Test
    public void ignoresMacroWithoutAttachments() {
        Set<String> used = used("<ac:structured-macro ac:name=\"code\">"
                + "<ac:plain-text-body>System.out.println();</ac:plain-text-body></ac:structured-macro>");

        assertTrue(used.toString(), used.isEmpty());
    }

    @Test
    public void ignoresEmptyBody() {
        assertTrue(used("").isEmpty());
    }

    @Test
    public void collectsEveryReferenceOfMixedBody() {
        Set<String> used = used("<h1>Заголовок</h1>"
                + "<p><ac:image><ri:attachment ri:filename=\"a.png\"/></ac:image></p>"
                + "<p><ac:link><ri:attachment ri:filename=\"b.pdf\"/></ac:link></p>"
                + "<ac:structured-macro ac:name=\"drawio\">"
                + "<ac:parameter ac:name=\"diagramName\">c</ac:parameter></ac:structured-macro>");

        assertTrue(used.containsAll(Set.of("a.png", "b.pdf", "c", "c.drawio.png")));
        assertFalse(used.contains("Заголовок"));
    }
}
