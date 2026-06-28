package ru.gitverse.adoct.parser.ast;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Превращает дерево {@link Block}/{@link Inline} в текст AsciiDoc.
 * <p>
 * Блоки разделяются ровно одной пустой строкой, ячейки таблицы раскладываются по строкам сразу
 * корректно — поэтому строковые пост-процессоры (схлопывание тройных переводов строк и компактизация
 * таблиц) больше не нужны. Состояние writer'а ({@code isLastReturn}, {@code topHeader}) тоже не требуется:
 * порядок и форматирование определяются структурой дерева.
 */
public final class AsciiDocWriter {

    public String write(List<Block> blocks) {
        return blocks.stream()
                .map(this::block)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("\n\n"));
    }

    private String block(Block node) {
        return switch (node) {
            case Block.Heading h -> "=".repeat(h.level() + 1) + " " + inline(h.title());
            case Block.Paragraph p -> inline(p.content());
            case Block.ItemList l -> list(l, 1);
            case Block.Table t -> table(t, '|');
            case Block.Admonition a -> admonition(a);
            case Block.RawBlock r -> r.adoc();
            case Block.Container c -> write(c.children());
        };
    }

    // --- списки ------------------------------------------------------------

    private String list(Block.ItemList list, int depth) {
        String marker = String.valueOf(list.ordered() ? '.' : '*').repeat(depth);
        StringBuilder sb = new StringBuilder();
        for (Block.ListItem item : list.items()) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(marker).append(' ').append(inline(item.text()));
            for (Block child : item.children()) {
                if (child instanceof Block.ItemList nested) {
                    sb.append('\n').append(list(nested, depth + 1));
                } else {
                    // присоединяем блок к элементу списка через continuation '+'
                    sb.append('\n').append('+').append('\n').append(block(child));
                }
            }
        }
        return sb.toString();
    }

    // --- таблицы -----------------------------------------------------------

    private String table(Block.Table table, char delim) {
        String sep = String.valueOf(delim);
        StringBuilder sb = new StringBuilder();
        sb.append("[cols=\"").append(table.cols()).append("\"]\n");
        sb.append(sep).append("===\n");
        for (Block.Row row : table.header()) {
            sb.append(headerRow(row, delim)).append('\n');
        }
        if (!table.header().isEmpty()) {
            sb.append('\n');
        }
        for (Block.Row row : table.body()) {
            for (Block.Cell cell : row.cells()) {
                sb.append(cell(cell, delim)).append('\n');
            }
            sb.append('\n');
        }
        sb.append(sep).append("===");
        return sb.toString();
    }

    /** Заголовочные ячейки — в одну строку. */
    private String headerRow(Block.Row row, char delim) {
        return row.cells().stream().map(c -> cell(c, delim)).collect(Collectors.joining(" "));
    }

    private String cell(Block.Cell cell, char delim) {
        StringBuilder sb = new StringBuilder(spanPrefix(cell));
        if (cell.isRich()) {
            // стиль ячейки — один символ: asciidoc 'a' имеет приоритет над header
            sb.append('a').append(delim);
            // вложенные таблицы внутри ячейки используют разделитель '!'
            sb.append(richCellBody(cell.blocks()));
        } else {
            if (cell.header()) {
                sb.append('h');
            }
            sb.append(delim).append(inline(cell.inline()));
        }
        return sb.toString();
    }

    private String richCellBody(List<Block> blocks) {
        return blocks.stream()
                .map(b -> b instanceof Block.Table t ? table(t, '!') : block(b))
                .collect(Collectors.joining("\n\n"));
    }

    /** Префикс colspan/rowspan: {@code 2+}, {@code .2+}, {@code 2.2+}. */
    private static String spanPrefix(Block.Cell cell) {
        boolean cs = cell.colspan() > 1;
        boolean rs = cell.rowspan() > 1;
        if (cs && rs) {
            return cell.colspan() + "." + cell.rowspan() + "+";
        }
        if (cs) {
            return cell.colspan() + "+";
        }
        if (rs) {
            return "." + cell.rowspan() + "+";
        }
        return "";
    }

    // --- admonition --------------------------------------------------------

    private String admonition(Block.Admonition a) {
        String title = a.title() != null && !a.title().isBlank() ? "." + a.title() + "\n" : "";
        return title + "[" + a.name() + "]\n====\n" + write(a.body()) + "\n====";
    }

    // --- инлайн ------------------------------------------------------------

    private String inline(List<Inline> nodes) {
        if (nodes == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Inline n : nodes) {
            sb.append(inline(n));
        }
        return sb.toString();
    }

    private String inline(Inline node) {
        return switch (node) {
            case Inline.Text t -> t.value();
            case Inline.Bold b -> "**" + inline(b.children()) + "**";
            case Inline.Italic i -> "__" + inline(i.children()) + "__";
            case Inline.Underline u -> "[.underline]##" + inline(u.children()) + "##";
            case Inline.Mono m -> "`" + inline(m.children()) + "`";
            case Inline.Colored c -> "[." + c.color() + "]##" + inline(c.children()) + "##";
            case Inline.Link l -> "link:" + l.url() + "[" + inline(l.label()) + "]";
            case Inline.LineBreak ignored -> " +\n";
            case Inline.Raw r -> r.adoc();
        };
    }
}
