package ru.gitverse.adoct.parser.doc;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import ru.gitverse.adoct.parser.PrintWriterReturn;

import java.io.PrintWriter;
import java.util.List;

public class ParseLayout implements ParseTags, SetterDispatcher {
    private PrintWriter print;
    private ParseDispatcher dispatcher;

    @Override
    public void setPrintWriter(PrintWriterReturn printWriter) {
        this.print = printWriter;
    }


    @Override
    public List<String> tags() {
        return List.of("ac:layout", "ac:layout-section", "ac:layout-cell");
    }

    @Override
    public void parse(Node node, ParseContext parseContext) {
        if (node instanceof Element element) {
            for (Element child : element.children()) {
                dispatcher.parse(child, parseContext);
            }
        }
    }

    @Override
    public void setDispatcher(ParseDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }
}
