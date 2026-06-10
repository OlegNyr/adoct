package ru.gitverse.adoct.parser.doc;

import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import ru.gitverse.adoct.parser.PrintWriterReturn;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static ru.gitverse.adoct.parser.doc.DispatherOption.PARSE_TRIM;

public class ParseTable implements ParseTags, SetterDispatcher {

    public static final Elements EMPTY = new Elements();
    private ParseDispatcher dispatcher;
    private PrintWriterReturn print;

    @Override
    public List<String> tags() {
        return List.of("table");
    }

    @Override
    public void parse(Node node, ParseContext parseContext) {
        if (node instanceof Element table) {
            //не поддерживаются таблицы больше одной вложенности
            Elements bodyRows = getBodyRows(table);
            if (parseContext.getInnerTable() > 1) {
                print.println();
                print.println("[source, text]");
                print.println("----");
                print.println(getHeadCols(table).stream().map(Element::text).collect(Collectors.joining("\\|")));
                print.println();
                print.println(bodyRows.stream().map(Element::text).collect(Collectors.joining("\\|")));

                print.println("----");
                print.println();
                return;
            }
            char symbol = parseContext.getInnerTable() == 0 ? '|' : '!';
            boolean ignoreFirst = false;
            boolean printHead = true;
            Elements headCols = colsFromHead(table);
            if (headCols.isEmpty()) {
                headCols = headFromBody(table);
                ignoreFirst = true;
            }
            if (headCols.isEmpty()) {
                printHead = false;
                headCols = headFromGroup(table);
            }

            String colsCount = getColsCount(headCols);
            print.println();
            print.printf("[cols=\"%s\"]", colsCount);

            print.println();
            print.println("%s===".formatted(symbol));
            //2+^|Требования и рекомендации к заполнению^|Пример
            ParseContext parseContextTrim = parseContext.addOption(PARSE_TRIM);
            if (printHead) {
                for (Element headCol : headCols) {
                    int colspan = getColSpan(headCol);
                    if (colspan > 1) {
                        if (print.isLastReturn())
                            print.printf("%d+%s", colspan, symbol);
                        else {
                            print.printf(" %d+%s", colspan, symbol);
                        }
                    } else {
                        print.print(symbol);
                    }
                    dispatcher.parseText(headCol, parseContext.addInnerTable());
                }
            }


            print.println();
            print.println();
            for (int j = 0; j < bodyRows.size(); j++) {
                Element bodyRow = bodyRows.get(j);
                if (ignoreFirst && j == 0) {
                    continue;
                }
                Elements cols = bodyRow.select(">td, >th");
                for (int i = 0; i < cols.size(); i++) {

                    Element col = cols.get(i);

                    int colSpan = getColSpan(col);
                    int rowSpan = getRowSpan(col);
                    if (colSpan > 1 && rowSpan > 1) {
                        if (!print.isLastReturn()) {
                            print.print(" ");
                        }
                        print.printf("%d.%d+", colSpan, rowSpan);
                    } else if (colSpan > 1) {
                        if (!print.isLastReturn()) {
                            print.print(" ");
                        }
                        print.printf("%d+", colSpan);
                    } else if (rowSpan > 1) {
                        if (!print.isLastReturn()) {
                            print.print(" ");
                        }
                        print.printf(".%d+", rowSpan);
                    }
                    print.print(symbol);
                    dispatcher.parseText(col, parseContext.addInnerTable());
                    print.println();
                }
                print.println();
            }

            print.println();
            print.println("%s===".formatted(symbol));
            print.println();
        } else {
            throw new IllegalArgumentException();
        }
    }

    private static Elements getBodyRows(Element table) {
        return table.select("> tbody > tr");
    }

    private static String getColsCount(Elements headCols) {
        return headCols.stream()
                .flatMap(h -> IntStream.range(0, getColSpan(h)).mapToObj(a -> "1a"))
                .collect(Collectors.joining(","));
    }

    private static Elements getHeadCols(Element table) {
        Elements headCols = colsFromHead(table);
        if (headCols.isEmpty()) {
            headCols = headFromBody(table);
        }
        if (headCols.isEmpty()) {
            headCols = headFromGroup(table);
        }
        return headCols;
    }

    private static @NotNull Elements headFromGroup(Element table) {
        return table.select("> colgroup > col ");
    }

    private static @NotNull Elements headFromBody(Element table) {
        Elements row = table.select(">tbody > tr");
        if (row.isEmpty()) {
            return EMPTY;
        }
        Element first = row.getFirst();
        Elements th = first.select("th");
        Elements td = first.select("td");
        if (td.isEmpty()) { //если есть обычные строки то явно не заголовок хотели сделать
            return th;
        } else {
            return EMPTY;
        }
    }

    private static @NotNull Elements colsFromHead(Element table) {
        return table.select("> thead > tr > td, > thead > tr > th ");
    }

    private static int getColSpan(Element element) {
        return Optional.ofNullable(element.attribute("colspan"))
                .map(Attribute::getValue)
                .map(Integer::parseInt)
                .orElse(1);
    }

    private static int getRowSpan(Element element) {
        return Optional.ofNullable(element.attribute("rowspan"))
                .map(Attribute::getValue)
                .map(Integer::parseInt)
                .orElse(1);
    }

    @Override
    public void setDispatcher(ParseDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }


    @Override
    public void setPrintWriter(PrintWriterReturn printWriter) {
        this.print = printWriter;
    }
}
