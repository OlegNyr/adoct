package io.github.adoct.parser.ast;

import java.util.List;

/**
 * Блочный узел документа — формат-нейтральный. Документ это {@code List<Block>}; конкретную разметку
 * (AsciiDoc или Markdown) и дисциплину пустых строк между блоками собирает {@link BlockWriter}
 * ({@link AsciiDocWriter} / {@link MarkdownWriter}) — за счёт чего не нужны строковые пост-процессоры.
 * <p>
 * Узлы не содержат готовой разметки: каждый writer сам решает, как отрендерить {@link CodeBlock},
 * {@link Image}, {@link Toc} и т.д. в своём формате.
 */
public sealed interface Block {

    /** Заголовок секции уровня {@code level} (1 = {@code ==} в AsciiDoc / {@code ##} в Markdown). */
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

    /** Элемент списка-задач (чекбокс) — {@code ac:task}: состояние + инлайн-текст. */
    record TaskItem(boolean checked, List<Inline> text) {
    }

    /** Список задач ({@code ac:task-list}) → чекбоксы {@code * [ ]}/{@code - [x]}. */
    record TaskList(List<TaskItem> items) implements Block {
    }

    /** Таблица: спецификация колонок ({@code cols=…}) + строки заголовка и тела. */
    record Table(String cols, List<Row> header, List<Row> body) implements Block {
    }

    /** Строка таблицы. */
    record Row(List<Cell> cells) {
    }

    /**
     * Ячейка таблицы. Простая — {@code inline}; rich (несколько блоков/вложенная таблица/список) —
     * {@code blocks}. Заполняется ровно одно из полей. {@code header} — это {@code <th>} в строке
     * тела (заголовочная колонка).
     */
    record Cell(int colspan, int rowspan, boolean header, List<Inline> inline, List<Block> blocks) {

        public boolean isRich() {
            return blocks != null && !blocks.isEmpty();
        }
    }

    /** Admonition (note/info/tip/warning): имя типа + опц. заголовок + тело. */
    record Admonition(String name, String title, List<Block> body) implements Block {
    }

    /** Confluence-панель → sidebar-блок с опц. заголовком; тело строится рекурсивно. */
    record Sidebar(String title, List<Block> body) implements Block {
    }

    /**
     * Блок кода / диаграммы. {@code content} — исходный текст (всегда доступен); {@code includePath}
     * не {@code null}, если длинный текст вынесен во внешний файл (AsciiDoc рендерит {@code include::},
     * Markdown инлайнит {@code content}). {@code language} — подсветка (в т.ч. {@code "plantuml"},
     * {@code "text"}); {@code title} — опц. заголовок блока.
     */
    record CodeBlock(String language, String title, String content, String includePath) implements Block {
    }

    /** Картинка блочного уровня: {@code target} — файл/путь, опц. {@code alt}/{@code width}/{@code height}. */
    record Image(String target, String alt, String width, String height, String caption) implements Block {
    }

    /** Якорь блочного уровня → {@code [#id]} (AsciiDoc) / {@code <a id="id"></a>} (Markdown). */
    record Anchor(String id) implements Block {
    }

    /** Оглавление → {@code toc::[]} (AsciiDoc). В Markdown опускается (GitHub строит TOC сам). */
    record Toc() implements Block {
    }

    /** Горизонтальный разделитель (шаги/табы) → {@code ---}. */
    record ThematicBreak() implements Block {
    }

    /** Нумерация разделов → {@code :sectnums:} (+ опц. уровни). В Markdown опускается. */
    record SectNums(String levels) implements Block {
    }

    /**
     * Трансклюзия страницы (Include Page): {@code folder} — имя папки страницы в наборе. AsciiDoc даёт
     * {@code include::folder/index.adoc[]}; Markdown — ссылку на {@code folder/index.md} (нет трансклюзии).
     */
    record PageInclude(String folder) implements Block {
    }

    /** Группа блоков без собственной разметки (section/layout/expand-тело). */
    record Container(List<Block> children) implements Block {
    }
}
