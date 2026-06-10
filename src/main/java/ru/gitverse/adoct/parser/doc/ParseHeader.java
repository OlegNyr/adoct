package ru.gitverse.adoct.parser.doc;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import ru.gitverse.adoct.parser.PrintWriterReturn;

import java.io.PrintWriter;
import java.util.List;

public class ParseHeader implements ParseTags, SetterDispatcher {
    private PrintWriterReturn print;
    private ParseDispatcher dispatcher;


    @Override
    public void setPrintWriter(PrintWriterReturn printWriter) {
        this.print = printWriter;
    }

    @Override
    public List<String> tags() {
        return List.of("h1", "h2", "h3", "h4");
    }

    @Override
    public void parse(Node element, ParseContext parseContext) {
        String name = element.nodeName();
        int number = Integer.parseInt(name.substring(1));
        parseContext.setTopHeader(number);
        print.println();
        print.println();
        print.printHeader(number);
        print.print(' ');
        if(element instanceof Element el) {
            dispatcher.parseText(el, parseContext.addOption(DispatherOption.PARSE_TRIM));
        }
        print.println();

    }

    @Override
    public void setDispatcher(ParseDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }
}
