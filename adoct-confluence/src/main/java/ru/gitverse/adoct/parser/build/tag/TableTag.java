package ru.gitverse.adoct.parser.build.tag;

import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.build.BlockBuilder;
import ru.gitverse.adoct.parser.build.BuildContext;
import ru.gitverse.adoct.parser.build.InlineBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Тег {@code <table>} → {@link Block.Table}.
 * <p>
 * Разбор опирается на структуру storage-формата Confluence, а не на наличие {@code <thead>}:
 * заголовок — это ведущие строки, целиком состоящие из {@code <th>} (покрывает и {@code <thead>},
 * и привычный «{@code <th>} в первой строке {@code <tbody>}», и несколько строк-заголовков).
 * Число колонок берётся из данных — максимум по строкам от суммы {@code colspan} — и не зависит от
 * угадывания заголовка. Заголовочные колонки ({@code <th>} в строках тела) рендерятся как {@code h|}.
 * Вложенность таблиц > 1 уровня дампится в {@code [source, text]}.
 */
public final class TableTag implements NodeTag {

    private static final Pattern WIDTH = Pattern.compile("width\\s*:\\s*(\\d+(?:\\.\\d+)?)");

    private final BlockBuilder blocks;
    private final InlineBuilder inline;

    public TableTag(BlockBuilder blocks, InlineBuilder inline) {
        this.blocks = blocks;
        this.inline = inline;
    }

    @Override
    public List<String> tags() {
        return List.of("table");
    }

    @Override
    public List<Block> build(Element table, BuildContext ctx) {
        if (ctx.tableDepth() > 1) {
            return List.of(dumpTable(table));
        }
        BuildContext cellCtx = ctx.withTableDepth(ctx.tableDepth() + 1);

        Elements rows = rows(table);
        int headerCount = leadingHeaderRows(rows);
        String cols = colsSpec(table, Math.max(columnCount(rows), 1));

        List<Block.Row> header = new ArrayList<>();
        List<Block.Row> body = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            boolean inHeader = i < headerCount;
            (inHeader ? header : body).add(row(rows.get(i), ctx, cellCtx, inHeader));
        }
        return List.of(new Block.Table(cols, header, body));
    }

    private Block.Row row(Element tr, BuildContext ctx, BuildContext cellCtx, boolean headerRow) {
        return new Block.Row(cells(tr).stream().map(c -> cell(c, ctx, cellCtx, headerRow)).toList());
    }

    private Block.Cell cell(Element col, BuildContext ctx, BuildContext cellCtx, boolean headerRow) {
        int colspan = span(col, "colspan");
        int rowspan = span(col, "rowspan");
        // <th> в строке тела — заголовочная колонка (h|); в строке-заголовке стиль не нужен.
        boolean header = !headerRow && col.normalName().equals("th");
        boolean rich = !col.select("table, ul, ol").isEmpty()
                || col.children().stream().filter(c -> c.normalName().equals("p")).count() > 1;
        if (rich) {
            return new Block.Cell(colspan, rowspan, header, null, blocks.build(col.children(), cellCtx));
        }
        return new Block.Cell(colspan, rowspan, header, inline.build(col, ctx), null);
    }

    /** Все строки таблицы в порядке документа (thead → tbody → tfoot). */
    private static Elements rows(Element table) {
        return table.select("> thead > tr, > tbody > tr, > tfoot > tr");
    }

    /** Кол-во ведущих строк, целиком состоящих из {@code <th>} (это заголовок). */
    private static int leadingHeaderRows(Elements rows) {
        int n = 0;
        for (Element tr : rows) {
            if (isAllHeader(tr)) {
                n++;
            } else {
                break;
            }
        }
        // если ВСЕ строки заголовочные — оставляем одну строку телом, чтобы не потерять данные
        return n == rows.size() && n > 0 ? n - 1 : n;
    }

    private static boolean isAllHeader(Element tr) {
        Elements cells = cells(tr);
        return !cells.isEmpty() && cells.stream().allMatch(c -> c.normalName().equals("th"));
    }

    /** Число колонок = максимум по строкам от суммы {@code colspan}. */
    private static int columnCount(Elements rows) {
        return rows.stream().mapToInt(TableTag::rowWidth).max().orElse(1);
    }

    private static int rowWidth(Element tr) {
        return cells(tr).stream().mapToInt(c -> span(c, "colspan")).sum();
    }

    private static Elements cells(Element tr) {
        return tr.select("> td, > th");
    }

    /**
     * Спецификация колонок. Если у таблицы есть {@code <colgroup>} с шириной у КАЖДОЙ колонки и их
     * число совпадает с фактическим — берём пропорциональные ширины ({@code 100a,300a}); иначе равные
     * ({@code 1a,1a,…}). Стиль {@code a} (asciidoc-ячейка) сохраняется на всех колонках.
     */
    private static String colsSpec(Element table, int columnCount) {
        List<Integer> widths = colgroupWidths(table);
        if (widths.size() == columnCount) {
            return widths.stream().map(w -> w + "a").collect(Collectors.joining(","));
        }
        return String.join(",", Collections.nCopies(columnCount, "1a"));
    }

    /** Ширины из {@code <colgroup><col>}; пустой список, если они есть не у всех колонок. */
    private static List<Integer> colgroupWidths(Element table) {
        Elements cols = table.select("> colgroup > col");
        List<Integer> widths = new ArrayList<>();
        for (Element col : cols) {
            Integer w = width(col);
            if (w == null) {
                return List.of();
            }
            widths.add(w);
        }
        return widths;
    }

    /** Ширина колонки из {@code style="width: 226.0px"} или атрибута {@code width}; {@code null} если нет/<=0. */
    private static Integer width(Element col) {
        Matcher m = WIDTH.matcher(col.attr("style"));
        if (m.find()) {
            int w = (int) Math.round(Double.parseDouble(m.group(1)));
            return w > 0 ? w : null;
        }
        String attr = col.attr("width").replaceAll("[^0-9.]", "");
        if (!attr.isBlank()) {
            try {
                int w = (int) Math.round(Double.parseDouble(attr));
                return w > 0 ? w : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** Дамп глубоко вложенной таблицы (> 1 уровня): первая строка как «шапка», остальные — телом. */
    private static Block dumpTable(Element table) {
        Elements rows = rows(table);
        String head = rows.isEmpty() ? "" : rowText(rows.getFirst());
        String body = rows.stream().skip(1).map(TableTag::rowText).collect(Collectors.joining("\n"));
        return new Block.RawBlock("[source, text]\n----\n" + head + "\n\n" + body + "\n----");
    }

    private static String rowText(Element tr) {
        return cells(tr).stream().map(Element::text).collect(Collectors.joining(" | "));
    }

    private static int span(Element element, String attr) {
        return Optional.ofNullable(element.attribute(attr))
                .map(Attribute::getValue)
                .map(Integer::parseInt)
                .orElse(1);
    }
}
