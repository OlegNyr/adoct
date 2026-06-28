package ru.gitverse.adoct.parser.ast;

import java.util.List;

/**
 * Блочный узел AsciiDoc. Документ — это {@code List<Block>}; разметку и дисциплину пустых строк
 * между блоками собирает {@link AsciiDocWriter} (за счёт чего не нужны пост-процессоры
 * {@code DubleCaretPostProcesing}/{@code TableCompactPostProcesing}).
 */
public sealed interface Block {

    /** Заголовок секции уровня {@code level} (1 = {@code ==}). */
    record Heading(int level, List<Inline> title) implements Block {
    }

    /** Абзац. */
    record Paragraph(List<Inline> content) implements Block {
    }

    /** Маркированный ({@code ordered=false}) или нумерованный список. */
    record ItemList(boolean ordered, List<ListItem> items) implements Block {
    }

    /** Элемент списка: инлайн-текст + вложенные блоки (в т.ч. вложенные списки/таблицы). */
    record ListItem(List<Inline> text, List<Block> children) {
    }

    /** Таблица: спецификация колонок ({@code cols=…}) + строки заголовка и тела. */
    record Table(String cols, List<Row> header, List<Row> body) implements Block {
    }

    /** Строка таблицы. */
    record Row(List<Cell> cells) {
    }

    /**
     * Ячейка таблицы. Простая — {@code inline} ({@code |текст}); rich (asciidoc-ячейка {@code a|})
     * — {@code blocks}. Заполняется ровно одно из полей. {@code header} — это {@code <th>} в строке
     * тела (заголовочная колонка) → рендерится со стилем {@code h|}.
     */
    record Cell(int colspan, int rowspan, boolean header, List<Inline> inline, List<Block> blocks) {

        public boolean isRich() {
            return blocks != null && !blocks.isEmpty();
        }
    }

    /** Admonition ({@code [NOTE]} и т.п.) с опц. заголовком и телом в delimited-блоке {@code ====}. */
    record Admonition(String name, String title, List<Block> body) implements Block {
    }

    /** Готовый самодостаточный AsciiDoc-блок (код, картинка, toc, anchor, plantuml, drawio…). */
    record RawBlock(String adoc) implements Block {
    }

    /** Группа блоков без собственной разметки (section/layout/expand-тело). */
    record Container(List<Block> children) implements Block {
    }
}
