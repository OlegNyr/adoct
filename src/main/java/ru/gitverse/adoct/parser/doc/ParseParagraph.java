package ru.gitverse.adoct.parser.doc;

import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import ru.gitverse.adoct.color.ColorParser;
import ru.gitverse.adoct.parser.PrintWriterReturn;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class ParseParagraph implements ParseTags, SetterDispatcher {
    private PrintWriterReturn print;
    @Setter
    private ParseDispatcher dispatcher;


    @Override
    public List<String> tags() {
        return List.of("p", "span", "a", "href");
    }

    @Override
    public void parse(Node element, ParseContext parseContext) {
        boolean isParagraph = element.nodeName().equalsIgnoreCase("p");
        if (isParagraph && !parseContext.isOption(DispatherOption.PARSE_TRIM)) {
            print.println();
        }

        for (Node node : element.childNodes()) {
            String name = node.nodeName();
            if (name.equalsIgnoreCase("#text")) {
                if (parseContext.isOption(DispatherOption.PARSE_TRIM)) {
                    print.print(normalStr(node));
                } else {
                    if (print.isLastReturn()) {
                        String text = StringUtils.stripStart(normalStr(node), null);
                        if (!text.isBlank()) {
                            print.print(text);
                        }
                    } else {
                        print.print(normalStr(node));
                    }
                }
            } else if (name.equalsIgnoreCase("span")) {
                if (!parseContext.isWorkColor()) {
                    parse(node, parseContext);
                    continue;
                }
                Optional<String> color = findColor(node.attr("style"));
                if (color.isEmpty()) {
                    parse(node, parseContext);
                    continue;
                }
                String text = parseInclude(node, parseContext);
                if (text.contains("#")) {
                    print.print(text);
                    continue;
                }
                if (text.isBlank()) {
                    continue;
                }
                // NOTE: ведущий '`' в начале строки — намеренно: строка, начинающаяся с "[.role]",
                //       иначе трактуется AsciiDoc как блочные атрибуты. Не убирать без проверки на реальном документе.
                if (print.isLastReturn()) {
                    print.print("`");
                }
                printText(text, color.get());

            } else if (name.equalsIgnoreCase("i")) {
                String text = parseInclude(node, parseContext);
                if (text.isBlank()) {
                    continue;
                }
                text = text.replace("__", "");
                print.print("__");
                print.print(text);
                print.print("__");
            } else if (name.equalsIgnoreCase("u")) {
                String text = parseInclude(node, parseContext);
                if (text.isBlank()) {
                    continue;
                }
                text = text.replace("#", "");
                if (print.isLastReturn()) {
                    print.print("`");
                }
                printText(text, "underline");
            } else if (name.equalsIgnoreCase("strong")) {
                //printSpaceIsLast();
                String text = parseInclude(node, parseContext);
                if (text.isBlank()) {
                    continue;
                }
                text = text.replace("**", "");
                print.print("**");
                print.print(text);
                print.print("**");
            } else if (name.equalsIgnoreCase("b")) {
                printSpaceIsLast();
                print.print("**");
                print.print(parseInclude(node, parseContext));
                print.print("**");
            } else if (name.equalsIgnoreCase("a")) {
                printSpaceIsLast();
                print.print("link:");
                print.print(node.attr("href"));
                print.print("[");
                parse(node, parseContext);
                print.print("] ");
            } else if (name.equalsIgnoreCase("br")) {
                print.println();
            } else {
                if (dispatcher.hasTag(node.nodeName())) {
                    dispatcher.parse((Element) node, parseContext);
                } else {

                    if (parseContext.isOption(DispatherOption.PARSE_TRIM)) {
                        print.print(StringUtils.trim(normalStr(node)));
                    } else {
                        print.print(normalStr(node));
                    }
                }
            }
        }
        if (isParagraph) {
            print.println();
        }

    }

    private void printText(String text, String name) {
        TextSpace textSpace = normalTextSpace(text);
        if (!textSpace.before().isEmpty()) {
            print.print(textSpace.before());
        }
        print.print("[.%s]##".formatted(name));
        print.print(textSpace.text());
        print.print("##");
        if (!textSpace.before().isEmpty()) {
            print.print(textSpace.after());
        }
    }

    private TextSpace normalTextSpace(String text) {
        int start = 0;
        int end = text.length();

        // Пропускаем пробелы в начале
        while (start < end && text.charAt(start) == ' ') {
            start++;
        }

        // Пропускаем пробелы в конце
        while (end > start && text.charAt(end - 1) == ' ') {
            end--;
        }

        // Извлекаем текст без пробелов по краям
        String trimmed = text.substring(start, end);

        // Формируем начальные и конечные пробелы
        String leadingSpaces = start > 0 ? text.substring(0, start) : "";
        String trailingSpaces = end < text.length() ? text.substring(end) : "";

        return new TextSpace(leadingSpaces, trailingSpaces, trimmed);
    }

    private Optional<String> findColor(String style) {
        return Arrays
                .stream(StringUtils.split(style, ";"))
                .filter(it -> it.startsWith("color:"))
                .flatMap(it -> ColorParser.parseColor(it).stream())
                .findFirst();
    }

    @SneakyThrows
    private String parseInclude(Node node, ParseContext parseContext) {
        try (StringWriter writer = new StringWriter();
             PrintWriterReturn printWriterReturn = new PrintWriterReturn(writer, true)) {

            ParseParagraph parseParagraph = new ParseParagraph();
            parseParagraph.setPrintWriter(printWriterReturn);
            parseParagraph.setDispatcher(dispatcher);
            parseParagraph.parse(node, parseContext);
            printWriterReturn.flush();
            writer.flush();

            return writer.toString();
        }
    }

    private void printSpaceIsLast() {
        if (!print.isLastReturn()) {
            print.print(" ");
        }
    }

    public static String normalStr(Node node) {
        return
                StringUtils.replaceEach(
                        ParseTags.getNodeText(node),
                        new String[]{"|"},
                        new String[]{"\\|"});
    }


    @Override
    public void setPrintWriter(PrintWriterReturn printWriter) {
        this.print = printWriter;
    }
}
