package ru.gitverse.adoct.parser.golden;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Фиксирует, что новый writer даёт чистый вывод без строковых пост-процессоров:
 * нет тройных переводов строк (бывший {@code DubleCaretPostProcesing}) и таблицы компактны
 * (бывший {@code TableCompactPostProcesing}).
 */
public class CleanOutputParserTest extends AbstractConvertParserTest {

    @Test
    public void noTripleNewlinesAcrossBlocks() throws IOException {
        String out = convert(
                "<h1>Заголовок</h1>"
                + "<p>Абзац один.</p>"
                + "<p>Абзац два.</p>"
                + "<ul><li>пункт</li></ul>"
                + "<ac:structured-macro ac:name=\"note\">"
                + "<ac:rich-text-body><p>заметка</p></ac:rich-text-body></ac:structured-macro>");
        assertFalse("остались тройные переводы строк", out.contains("\n\n\n"));
        assertTrue(out.contains("== Заголовок"));
        assertTrue(out.contains("[NOTE]"));
    }

    @Test
    public void tableHasExactCompactLayout() throws IOException {
        String out = convert(
                "<table><thead><tr><th>H1</th><th>H2</th></tr></thead>"
                + "<tbody><tr><td>a</td><td>b</td></tr></tbody></table>");
        assertEquals(
                "= Документ\n"
                + ":toc: macro\n"
                + ":imagesdir: ./attache\n"
                + "\n"
                + "[cols=\"1a,1a\"]\n"
                + "|===\n"
                + "|H1 |H2\n"
                + "\n"
                + "|a\n"
                + "|b\n"
                + "\n"
                + "|===\n",
                out);
    }
}
