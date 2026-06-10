package ru.gitverse.adoct.parser.doc;

import org.jsoup.nodes.Node;
import ru.gitverse.adoct.parser.PrintWriterReturn;

import java.io.PrintWriter;
import java.util.List;

public class ParseTime implements ParseTags {
    private PrintWriter print;


    @Override
    public void setPrintWriter(PrintWriterReturn printWriter) {
        this.print = printWriter;
    }

    @Override
    public List<String> tags() {
        return List.of("time");
    }

    @Override
    public void parse(Node element, ParseContext parseContext) {
        print.print(element.attr("datetime"));

    }

}
