package ru.gitverse.adoct.parser.doc;

import org.jsoup.nodes.Node;
import ru.gitverse.adoct.parser.PrintWriterReturn;

import java.util.List;

public class ParsePlaceholder implements ParseTags {
    @Override
    public void setPrintWriter(PrintWriterReturn printWriter) {

    }

    @Override
    public List<String> tags() {
        return List.of("ac:placeholder");
    }

    @Override
    public void parse(Node element, ParseContext parseContext) {

    }
}
