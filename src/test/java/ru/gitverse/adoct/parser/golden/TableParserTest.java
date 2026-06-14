package ru.gitverse.adoct.parser.golden;

import org.junit.Test;

import java.io.IOException;

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
     * Таблица без заголовка (только {@code <td>}) сохраняет ВСЕ строки тела.
     * Раньше движок молча терял первую строку (баг {@code ignoreFirst}); новый разбор по «вся строка
     * из {@code <th>} → заголовок» не выбрасывает строки.
     */
    @Test
    public void tableWithoutHeadKeepsAllBodyRows() throws IOException {
        String out = convert(
                "<table><tbody>"
                + "<tr><td>первая</td></tr>"
                + "<tr><td>вторая</td></tr>"
                + "</tbody></table>");
        assertTrue(out.contains("первая"));
        assertTrue(out.contains("вторая"));
    }

    /** Ширины колонок из {@code <colgroup>} → пропорциональный {@code [cols]} (с сохранением стиля {@code a}). */
    @Test
    public void colgroupWidthsBecomeProportionalCols() throws IOException {
        String out = convert(
                "<table><colgroup>"
                + "<col style=\"width: 100.0px;\"/><col style=\"width: 300.0px;\"/></colgroup>"
                + "<tbody><tr><th>H1</th><th>H2</th></tr>"
                + "<tr><td>a</td><td>b</td></tr></tbody></table>");
        assertTrue(out.contains("[cols=\"100a,300a\"]"));
    }

    /** Без полного {@code <colgroup>} — равные колонки {@code 1a}. */
    @Test
    public void missingColgroupFallsBackToEqualCols() throws IOException {
        String out = convert(
                "<table><tbody><tr><th>H1</th><th>H2</th></tr>"
                + "<tr><td>a</td><td>b</td></tr></tbody></table>");
        assertTrue(out.contains("[cols=\"1a,1a\"]"));
    }

    /** Заголовочная колонка: {@code <th>} в строке тела рендерится со стилем {@code h|}. */
    @Test
    public void headerColumnCellsUseHStyle() throws IOException {
        String out = convert(
                "<table><tbody>"
                + "<tr><th>Имя</th><td>Иван</td></tr>"
                + "<tr><th>Возраст</th><td>30</td></tr>"
                + "</tbody></table>");
        assertTrue(out.contains("h|Имя"));
        assertTrue(out.contains("h|Возраст"));
        assertTrue(out.contains("|Иван"));
    }
}
