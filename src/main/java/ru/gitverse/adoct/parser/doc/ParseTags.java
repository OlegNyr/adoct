package ru.gitverse.adoct.parser.doc;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import ru.gitverse.adoct.parser.PrintWriterReturn;

import java.io.PrintWriter;
import java.util.List;

public interface ParseTags {
    void setPrintWriter(PrintWriterReturn printWriter);

    List<String> tags();

    default boolean isWork(Element element) {
        return true;
    }

    void parse(Node element, ParseContext parseContext);

    static String getNodeText(Node node) {
        if (node instanceof Element e) {
            return e.text();
        }
        if (node instanceof TextNode e) {
            return e.text();
        } else {
            return node.outerHtml();
        }

    }
}
