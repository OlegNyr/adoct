package ru.gitverse.adoct.parser.doc;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import ru.gitverse.adoct.parser.PrintWriterReturn;

import java.io.PrintWriter;
import java.util.List;

public class ParseList implements ParseTags, SetterDispatcher {
    private ParseDispatcher dispatcher;
    private PrintWriter print;

    @Override
    public void setPrintWriter(PrintWriterReturn printWriter) {
        print = printWriter;
    }

    @Override
    public List<String> tags() {
        return List.of("ul", "li", "ol");
    }

    @Override
    public void parse(Node element, ParseContext parseContext) {
        if (element.nodeName().equalsIgnoreCase("li")) {
            print.println();
            print.println();
            // Маркер по типу родителя: ol -> нумерованный '.', ul (и всё прочее) -> '*'.
            // Уровень = глубина вложенности; смешанные списки получают маркер по своему ближайшему родителю.
            boolean ordered = element.parentNode() != null
                    && element.parentNode().nodeName().equalsIgnoreCase("ol");
            char marker = ordered ? '.' : '*';
            print.print(StringUtils.repeat(marker, parseContext.getListLevel()));
            print.print(" ");
            dispatcher.parseText(element, parseContext.addOption(DispatherOption.PARSE_TRIM));
            print.println();
        } else if (element.nodeName().equalsIgnoreCase("ul")
                   || element.nodeName().equalsIgnoreCase("ol")) {
            if (element instanceof Element el) {
                print.println();
                dispatcher.parse(el.children(), parseContext.addAddLevel());
            }
        }
    }

    @Override
    public void setDispatcher(ParseDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }
}
