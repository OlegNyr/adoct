package ru.gitverse.adoct.parser.golden;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Таблицы: cols, разделители, экранирование '|', и quirk с потерей первой строки. */
public class TableParserTest extends AbstractConvertParserTest {

    @Test
    public void tableWithHeadProducesColsAndDelimiters() throws IOException {
        String out = convert(
                "<table><thead><tr><th>Колонка1</th><th>Колонка2</th></tr></thead>"
                + "<tbody><tr><td>знач1</td><td>знач2</td></tr></tbody></table>");
        assertTrue(out.contains("[cols=\"1a,1a\"]"));
        assertTrue(out.contains("|==="));
        assertTrue(out.contains("|Колонка1"));
        assertTrue(out.contains("|Колонка2"));
        assertTrue(out.contains("|знач1"));
        assertTrue(out.contains("|знач2"));
    }

    @Test
    public void tableCellPipeIsEscaped() throws IOException {
        String out = convert(
                "<table><thead><tr><th>H</th></tr></thead>"
                + "<tbody><tr><td>a|b</td></tr></tbody></table>");
        // символ '|' внутри ячейки экранируется как \|, чтобы не ломать разметку таблицы
        assertTrue(out.contains("a\\|b"));
    }

    /**
     * Quirk (фиксируем как есть, не баг-фикс): таблица без {@code <thead>} и без ячеек {@code <th>}
     * молча теряет ПЕРВУЮ строку {@code <tbody>}. В {@code ParseTable} флаг {@code ignoreFirst}
     * выставляется при пустом {@code colsFromHead}, даже если {@code headFromBody} ничего не вернул,
     * после чего строка с индексом 0 пропускается.
     */
    @Test
    public void tableWithoutHeadDropsFirstBodyRow() throws IOException {
        String out = convert(
                "<table><tbody>"
                + "<tr><td>первая</td></tr>"
                + "<tr><td>вторая</td></tr>"
                + "</tbody></table>");
        assertFalse(out.contains("первая"));
        assertTrue(out.contains("вторая"));
    }
}
