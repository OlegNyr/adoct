package io.github.adoct.parser.ast;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Превращает дерево {@link Block}/{@link Inline} в текст AsciiDoc.
 * <p>
 * Блоки разделяются ровно одной пустой строкой, ячейки таблицы раскладываются по строкам сразу
 * корректно — поэтому строковые пост-процессоры (схлопывание тройных переводов строк и компактизация
 * таблиц) не нужны. Экранирование ({@code |}/{@code !} в ячейках, {@code ]} в подписях ссылок) —
 * здесь, под синтаксис AsciiDoc, а не в дереве.
 */
public final class AsciiDocWriter implements BlockWriter {

    @Override
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
            case Block.TaskList t -> taskList(t);
            case Block.Table t -> table(t, '|');
            case Block.Admonition a -> admonition(a);
            case Block.Sidebar s -> sidebar(s);
            case Block.CodeBlock c -> codeBlock(c);
            case Block.Image i -> image("image::", i.target(), i.alt(), i.width(), i.height(), i.caption());
            case Block.Anchor a -> "[#%s]".formatted(a.id());
            case Block.Toc ignored -> "toc::[]";
            case Block.ThematicBreak ignored -> "---";
            case Block.SectNums s -> sectNums(s);
            case Block.PageInclude p -> "include::%s/index.adoc[]".formatted(p.folder());
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

    private String taskList(Block.TaskList list) {
        return list.items().stream()
                .map(i -> "* [%s] %s".formatted(i.checked() ? "x" : " ", inline(i.text())))
                .collect(Collectors.joining("\n"));
    }

    // --- код / картинки / атрибуты -----------------------------------------

    private String codeBlock(Block.CodeBlock c) {
        StringBuilder sb = new StringBuilder();
        if (c.title() != null && !c.title().isBlank()) {
            sb.append('.').append(c.title()).append('\n');
        }
        sb.append(header(c.language())).append("\n----\n");
        sb.append(c.includePath() != null ? "include::%s[]".formatted(c.includePath()) : c.content());
        sb.append("\n----");
        return sb.toString();
    }

    private static String header(String language) {
        if ("plantuml".equals(language)) {
            return "[plantuml, format=\"png\"]";
        }
        return language == null ? "[source]" : "[source, %s]".formatted(language);
    }

    private String image(String prefix, String target, String alt, String width, String height, String caption) {
        String cap = caption != null && !caption.isBlank() ? "." + caption + "\n" : "";
        return cap + prefix + target + "[" + imageParams(alt, width, height) + "]";
    }

    /** Позиционный alt, когда нет размеров; именованные атрибуты, когда есть width/height. */
    private static String imageParams(String alt, String width, String height) {
        boolean hasDims = notEmpty(width) || notEmpty(height);
        if (!hasDims) {
            return alt == null ? "" : alt;
        }
        StringBuilder sb = new StringBuilder();
        if (notEmpty(alt)) {
            sb.append("alt=").append(alt);
        }
        if (notEmpty(width)) {
            append(sb, "width=" + width);
        }
        if (notEmpty(height)) {
            append(sb, "height=" + height);
        }
        return sb.toString();
    }

    private static void append(StringBuilder sb, String part) {
        if (!sb.isEmpty()) {
            sb.append(',');
        }
        sb.append(part);
    }

    private static String sectNums(Block.SectNums s) {
        return notEmpty(s.levels()) ? ":sectnums:\n:sectnumslevels: " + s.levels() : ":sectnums:";
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
            sb.append(delim).append(escapeCell(inline(cell.inline()), delim));
        }
        return sb.toString();
    }

    /** Экранирует разделитель ячеек ({@code |} или вложенный {@code !}) в тексте простой ячейки. */
    private static String escapeCell(String text, char delim) {
        return text.replace(String.valueOf(delim), "\\" + delim);
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

    private String sidebar(Block.Sidebar s) {
        String title = s.title() != null && !s.title().isBlank() ? "." + s.title() + "\n" : "";
        return title + "****\n" + write(s.body()) + "\n****";
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
            case Inline.Link l -> "link:" + l.url() + "[" + linkLabel(l.label()) + "]";
            case Inline.CrossRef x -> "<<%s, %s>>".formatted(x.anchor(), x.text());
            case Inline.Image i -> image("image:", i.target(), i.alt(), i.width(), i.height(), null);
            case Inline.Status s -> "[.%s]#%s#".formatted(s.colour() != null
                    ? "status-" + s.colour().toLowerCase(java.util.Locale.ROOT) : "status", s.title());
            case Inline.LineBreak ignored -> " +\n";
        };
    }

    /** Подпись ссылки; для plain-text (резолвнутые ac:link) экранируем {@code ]}. */
    private String linkLabel(List<Inline> label) {
        String rendered = inline(label);
        boolean allText = label != null && label.stream().allMatch(n -> n instanceof Inline.Text);
        return allText ? rendered.replace("]", "\\]") : rendered;
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }
}
