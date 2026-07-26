package io.github.adoct.parser.ast;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Превращает дерево {@link Block}/{@link Inline} в GitHub Flavored Markdown.
 * <p>
 * То же дерево, что и у {@link AsciiDocWriter}, но с маппингом под GFM: admonition → alert-цитаты
 * ({@code > [!NOTE]}), таблицы с colspan/rowspan или rich-ячейками → HTML {@code <table>},
 * подчёркивание/цвет → inline-HTML, {@code |} в ячейках экранируется, nbsp вокруг {@code **}/{@code *}
 * выносится наружу. Заголовок/frontmatter документа добавляет вызывающий, не writer.
 */
public final class MarkdownWriter implements BlockWriter {

    /** Папка картинок для относительных путей (у AsciiDoc её роль играет {@code :imagesdir:}). */
    private final String imagesDir;

    public MarkdownWriter() {
        this(null);
    }

    public MarkdownWriter(String imagesDir) {
        this.imagesDir = imagesDir == null || imagesDir.isBlank() ? null : imagesDir;
    }

    @Override
    public String write(List<Block> blocks) {
        return blocks.stream()
                .map(this::block)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("\n\n"));
    }

    private String block(Block node) {
        return switch (node) {
            case Block.Heading h -> "#".repeat(h.level() + 1) + " " + inline(h.title());
            case Block.Paragraph p -> inline(p.content());
            case Block.ItemList l -> list(l, "");
            case Block.TaskList t -> taskList(t);
            case Block.Table t -> table(t);
            case Block.Admonition a -> admonition(a);
            case Block.Sidebar s -> sidebar(s);
            case Block.CodeBlock c -> codeBlock(c);
            case Block.Image i -> image(i.target(), i.alt(), i.width(), i.height(), i.caption());
            case Block.Anchor a -> "<a id=\"%s\"></a>".formatted(a.id());
            case Block.Toc ignored -> "";
            case Block.ThematicBreak ignored -> "---";
            case Block.SectNums ignored -> "";
            case Block.PageInclude p -> "[%s](%s/index.md)".formatted(p.folder(), p.folder());
            case Block.Container c -> write(c.children());
        };
    }

    // --- списки ------------------------------------------------------------

    private String list(Block.ItemList list, String indent) {
        StringBuilder sb = new StringBuilder();
        int n = 1;
        for (Block.ListItem item : list.items()) {
            String marker = list.ordered() ? (n++) + "." : "-";
            String childIndent = indent + " ".repeat(marker.length() + 1);
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(indent).append(marker).append(' ').append(inline(item.text()));
            for (Block child : item.children()) {
                if (child instanceof Block.ItemList nested) {
                    sb.append('\n').append(list(nested, childIndent));
                } else {
                    // вложенный блок в элементе списка: пустая строка + отступ по правилам GFM
                    sb.append("\n\n").append(indentLines(block(child), childIndent));
                }
            }
        }
        return sb.toString();
    }

    private String taskList(Block.TaskList list) {
        return list.items().stream()
                .map(i -> "- [%s] %s".formatted(i.checked() ? "x" : " ", inline(i.text())))
                .collect(Collectors.joining("\n"));
    }

    private static String indentLines(String text, String indent) {
        return text.lines().map(l -> l.isEmpty() ? l : indent + l).collect(Collectors.joining("\n"));
    }

    // --- код / картинки ----------------------------------------------------

    private String codeBlock(Block.CodeBlock c) {
        String lang = c.language() == null ? "" : c.language();
        String title = c.title() != null && !c.title().isBlank() ? "**" + escape(c.title()) + "**\n\n" : "";
        return title + "```" + lang + "\n" + c.content() + "\n```";
    }

    private String image(String target, String alt, String width, String height, String caption) {
        target = imagePath(target);
        String text = notBlank(caption) ? caption : (alt == null ? "" : alt);
        String img;
        if (notBlank(width) || notBlank(height)) {
            img = "<img src=\"%s\" alt=\"%s\"%s%s>".formatted(target, text == null ? "" : text,
                    notBlank(width) ? " width=\"" + width + "\"" : "",
                    notBlank(height) ? " height=\"" + height + "\"" : "");
        } else {
            img = "![%s](%s)".formatted(text, target);
        }
        return notBlank(caption) && !(notBlank(width) || notBlank(height)) ? img + "\n\n*" + escape(caption) + "*" : img;
    }

    // --- admonition / sidebar ---------------------------------------------

    private String admonition(Block.Admonition a) {
        String alert = switch (a.name() == null ? "" : a.name().toUpperCase(Locale.ROOT)) {
            case "TIP" -> "TIP";
            case "WARNING" -> "WARNING";
            case "CAUTION" -> "CAUTION";
            case "IMPORTANT" -> "IMPORTANT";
            default -> "NOTE";
        };
        StringBuilder body = new StringBuilder("[!").append(alert).append(']');
        if (a.title() != null && !a.title().isBlank()) {
            body.append('\n').append("**").append(escape(a.title())).append("**");
        }
        String inner = write(a.body());
        if (!inner.isEmpty()) {
            body.append('\n').append(inner);
        }
        return quote(body.toString());
    }

    private String sidebar(Block.Sidebar s) {
        StringBuilder body = new StringBuilder();
        if (s.title() != null && !s.title().isBlank()) {
            body.append("**").append(escape(s.title())).append("**");
        }
        String inner = write(s.body());
        if (!inner.isEmpty()) {
            if (!body.isEmpty()) {
                body.append('\n');
            }
            body.append(inner);
        }
        return quote(body.toString());
    }

    /** Оборачивает текст в blockquote ({@code > } на каждой строке). */
    private static String quote(String text) {
        return text.lines().map(l -> l.isEmpty() ? ">" : "> " + l).collect(Collectors.joining("\n"));
    }

    // --- таблицы -----------------------------------------------------------

    private String table(Block.Table table) {
        return isGfmable(table) ? gfmTable(table) : htmlTable(table);
    }

    /** GFM-таблица допустима: одна строка заголовка, без colspan/rowspan и без rich-ячеек. */
    private static boolean isGfmable(Block.Table table) {
        if (table.header().size() > 1) {
            return false;
        }
        for (Block.Row row : allRows(table)) {
            for (Block.Cell cell : row.cells()) {
                if (cell.colspan() > 1 || cell.rowspan() > 1 || cell.isRich()) {
                    return false;
                }
            }
        }
        return true;
    }

    private String gfmTable(Block.Table table) {
        List<Block.Row> body = table.body();
        int columns = table.body().stream().mapToInt(r -> r.cells().size())
                .max().orElse(table.header().isEmpty() ? 1 : table.header().getFirst().cells().size());
        List<String> header = table.header().isEmpty()
                ? java.util.Collections.nCopies(columns, "")
                : table.header().getFirst().cells().stream().map(this::gfmCell).toList();
        columns = Math.max(columns, header.size());

        StringBuilder sb = new StringBuilder();
        sb.append(gfmRow(header, columns)).append('\n');
        sb.append("|").append(" --- |".repeat(columns)).append('\n');
        for (Block.Row row : body) {
            List<String> cells = row.cells().stream().map(this::gfmCell).toList();
            sb.append(gfmRow(cells, columns)).append('\n');
        }
        return sb.substring(0, sb.length() - 1);
    }

    private static String gfmRow(List<String> cells, int columns) {
        StringBuilder sb = new StringBuilder("|");
        for (int i = 0; i < columns; i++) {
            sb.append(' ').append(i < cells.size() ? cells.get(i) : "").append(" |");
        }
        return sb.toString();
    }

    private String gfmCell(Block.Cell cell) {
        return inline(cell.inline()).replace("|", "\\|").replace("\n", "<br>");
    }

    private String htmlTable(Block.Table table) {
        StringBuilder sb = new StringBuilder("<table>\n");
        for (Block.Row row : table.header()) {
            sb.append(htmlRow(row, true));
        }
        for (Block.Row row : table.body()) {
            sb.append(htmlRow(row, false));
        }
        sb.append("</table>");
        return sb.toString();
    }

    private String htmlRow(Block.Row row, boolean headerRow) {
        StringBuilder sb = new StringBuilder("  <tr>\n");
        for (Block.Cell cell : row.cells()) {
            String tag = headerRow || cell.header() ? "th" : "td";
            sb.append("    <").append(tag).append(spanAttrs(cell)).append('>')
                    .append(cellHtml(cell)).append("</").append(tag).append(">\n");
        }
        sb.append("  </tr>\n");
        return sb.toString();
    }

    private static String spanAttrs(Block.Cell cell) {
        StringBuilder sb = new StringBuilder();
        if (cell.colspan() > 1) {
            sb.append(" colspan=\"").append(cell.colspan()).append('"');
        }
        if (cell.rowspan() > 1) {
            sb.append(" rowspan=\"").append(cell.rowspan()).append('"');
        }
        return sb.toString();
    }

    /** Содержимое ячейки для HTML-таблицы: inline-markdown для простой, HTML-эквивалент блоков для rich. */
    private String cellHtml(Block.Cell cell) {
        if (!cell.isRich()) {
            return inline(cell.inline());
        }
        return cell.blocks().stream().map(this::blockAsHtml).collect(Collectors.joining("<br>"));
    }

    /** Блок как inline-HTML (для вложения в {@code <td>}, где блочный markdown не работает). */
    private String blockAsHtml(Block node) {
        return switch (node) {
            case Block.Paragraph p -> inline(p.content());
            case Block.Table t -> htmlTable(t);
            case Block.ItemList l -> htmlList(l);
            case Block.Admonition a -> emoji(a.name()) + " " + a.body().stream()
                    .map(this::blockAsHtml).collect(Collectors.joining("<br>"));
            case Block.CodeBlock c -> "<pre><code>" + escapeHtml(c.content()) + "</code></pre>";
            case Block.Image i -> image(i.target(), i.alt(), i.width(), i.height(), i.caption());
            case Block.Container c -> c.children().stream().map(this::blockAsHtml)
                    .collect(Collectors.joining("<br>"));
            default -> block(node).replace("\n", "<br>");
        };
    }

    private String htmlList(Block.ItemList list) {
        String tag = list.ordered() ? "ol" : "ul";
        String items = list.items().stream()
                .map(i -> "<li>" + inline(i.text()) + "</li>")
                .collect(Collectors.joining());
        return "<" + tag + ">" + items + "</" + tag + ">";
    }

    /** Эмодзи-префикс admonition для ячеек таблицы (blockquote в GFM-таблице невозможен). */
    private static String emoji(String name) {
        return switch (name == null ? "" : name.toUpperCase(Locale.ROOT)) {
            case "TIP" -> "💡";
            case "WARNING", "CAUTION" -> "⚠️";
            case "IMPORTANT" -> "❗";
            default -> "📝";
        };
    }

    private static List<Block.Row> allRows(Block.Table table) {
        return java.util.stream.Stream.concat(table.header().stream(), table.body().stream()).toList();
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
            case Inline.Text t -> escape(t.value());
            case Inline.Bold b -> emphasis("**", inline(b.children()));
            case Inline.Italic i -> emphasis("*", inline(i.children()));
            case Inline.Underline u -> "<u>" + inline(u.children()) + "</u>";
            case Inline.Mono m -> "`" + plain(m.children()) + "`";
            case Inline.Colored c -> "<span style=\"color: %s\">%s</span>".formatted(c.color(), inline(c.children()));
            case Inline.Link l -> link(l);
            case Inline.CrossRef x -> "[%s](#%s)".formatted(escape(x.text()), x.anchor());
            case Inline.Image i -> image(i.target(), i.alt(), i.width(), i.height(), null);
            case Inline.Status s -> "`" + s.title() + "`";
            case Inline.LineBreak ignored -> "\\\n";
        };
    }

    private String link(Inline.Link l) {
        String label = inline(l.label());
        if (label.isEmpty()) {
            return "<" + l.url() + ">";
        }
        return "[%s](%s)".formatted(label.replace("]", "\\]"), l.url());
    }

    /** Текст без markdown-разметки (для содержимого inline-кода). */
    private String plain(List<Inline> nodes) {
        StringBuilder sb = new StringBuilder();
        for (Inline n : nodes) {
            if (n instanceof Inline.Text t) {
                sb.append(t.value());
            } else if (n instanceof Inline.Bold b) {
                sb.append(plain(b.children()));
            } else if (n instanceof Inline.Italic i) {
                sb.append(plain(i.children()));
            } else if (n instanceof Inline.Mono m) {
                sb.append(plain(m.children()));
            } else if (n instanceof Inline.Underline u) {
                sb.append(plain(u.children()));
            } else {
                sb.append(inline(n));
            }
        }
        return sb.toString();
    }

    /** Оборачивает в маркеры эмфазы, вынося ведущие/замыкающие пробелы (в т.ч. nbsp) наружу. */
    private static String emphasis(String marker, String inner) {
        int start = 0;
        while (start < inner.length() && isSpace(inner.charAt(start))) {
            start++;
        }
        int end = inner.length();
        while (end > start && isSpace(inner.charAt(end - 1))) {
            end--;
        }
        if (start >= end) {
            return inner;
        }
        return inner.substring(0, start) + marker + inner.substring(start, end) + marker + inner.substring(end);
    }

    private static boolean isSpace(char c) {
        return Character.isWhitespace(c) || c == 0x00A0 || c == 0x200B || c == 0xFEFF;
    }

    /**
     * Экранирование markdown-спецсимволов в тексте (структурно опасных в inline-контексте). {@code |}
     * здесь не трогаем — он значим только в ячейках таблиц, где его экранирует {@link #gfmCell}.
     */
    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '`' || c == '*' || c == '_' || c == '[' || c == ']' || c == '<') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Префикс папки картинок для «голых» имён файлов (у которых нет пути/схемы). */
    private String imagePath(String target) {
        if (imagesDir == null || target == null || target.contains("/") || target.contains(":")) {
            return target;
        }
        return imagesDir + "/" + target;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
