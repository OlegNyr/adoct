package ru.gitverse.adoct.parser.doc;

import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import ru.gitverse.adoct.parser.PrintWriterReturn;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class ParseImgAcc implements ParseTags, SetterDispatcher {
    @Setter
    private PrintWriterReturn printWriter;
    @Setter
    private ParseDispatcher dispatcher;


    @Override
    public List<String> tags() {
        return List.of("ac:image");
    }

    @SneakyThrows
    @Override
    public void parse(Node node, ParseContext parseContext) {
        if (!(node instanceof Element e)) {
            return;
        }
        if (!printWriter.isLastReturn()) {
            printWriter.print(" ");
            printWriter.print("image:");
        } else {
            printWriter.println();
            printWriter.print("image::");
        }

        String filenameRaw = e.getElementsByTag("ri:attachment").attr("ri:filename");


        printWriter.print(filenameRaw);
        printWriter.print("[");
        List<String> params = new ArrayList<>();
        parseInclude(node, parseContext)
                .ifPresent(text -> params.add("alt=%s".formatted(text)));
        Optional.of(e.attr("ac:width"))
                .filter(StringUtils::isNotEmpty)
                .ifPresent(v -> params.add("width=%s".formatted(v)));
        Optional.of(e.attr("ac:height"))
                .filter(StringUtils::isNotEmpty)
                .ifPresent(v -> params.add("height=%s".formatted(v)));
        if (!params.isEmpty()) {
            printWriter.print(String.join(",", params));
        }
        printWriter.print("]");


    }

    @SneakyThrows
    private Optional<String> parseInclude(Node node, ParseContext parseContext) {
        try (StringWriter writer = new StringWriter();
             PrintWriterReturn printWriterReturn = new PrintWriterReturn(writer, true)) {

            ParseParagraph parseParagraph = new ParseParagraph();
            parseParagraph.setPrintWriter(printWriterReturn);
            parseParagraph.setDispatcher(dispatcher);
            parseParagraph.parse(node, parseContext.addOption(DispatherOption.PARSE_TRIM));
            printWriterReturn.flush();
            writer.flush();

            return Optional.ofNullable(writer.toString()).filter(StringUtils::isNotEmpty);
        }
    }

}
