package ru.gitverse.adoct.parser.doc;

import org.jsoup.nodes.Node;
import ru.gitverse.adoct.parser.PrintWriterReturn;

import java.util.List;

public class ParseCommentId implements ParseTags, SetterDispatcher {
    private PrintWriterReturn print;
    private ParseDispatcher dispatcher;


    @Override
    public void setPrintWriter(PrintWriterReturn printWriter) {
        this.print = printWriter;
    }

    @Override
    public List<String> tags() {
        return List.of("ac:inline-comment-marker");
    }

    @Override
    public void parse(Node node, ParseContext parseContext) {
        if (!print.isLastReturn()) {
            print.print(" ");
        }
        dispatcher.parseText(node, parseContext);
        print.print(" ");

    }

    @Override
    public void setDispatcher(ParseDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }
}
