package ru.gitverse.adoct.parser.doc;

import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import ru.gitverse.adoct.parser.PrintWriterReturn;
import ru.gitverse.adoct.parser.macros.ParseMacrosDispatcher;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ParseTagMacros implements ParseTags, SetterDispatcher {
    private final ParseMacrosDispatcher parseMacrosDispatcher;
    private PrintWriterReturn printWriter;
    private ParseDispatcher dispatcher;

    public ParseTagMacros(ParseMacrosDispatcher parseMacrosDispatcher) {
        this.parseMacrosDispatcher = parseMacrosDispatcher;
    }

    @Override
    public List<String> tags() {
        return List.of("ac:structured-macro");
    }

    @Override
    public void parse(Node node, ParseContext parseContext) {
        if (node instanceof Element element) {
            String name = element.attr("ac:name");
            Map<String, String> parameter = getParameters(element);
            Element body = childByName(element, "ac:rich-text-body")
                    .findFirst()
                    .or(() -> childByName(element, "ac:plain-text-body").findFirst())
                    .orElse(null);
            parseMacrosDispatcher.parse(name, parameter, body, parseContext);
        }
    }


    private static @NotNull Map<String, String> getParameters(Element element) {
        return childByName(element, "ac:parameter")
                .map(e -> Map.entry(e.attr("ac:name"), e.html()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static @NotNull Stream<Element> childByName(Element element, String tagName) {
        return element.children().stream().filter(e -> e.nodeName().equals(tagName));
    }


    @Override
    public void setPrintWriter(PrintWriterReturn printWriter) {
        this.printWriter = printWriter;
        this.parseMacrosDispatcher.setPrinter(printWriter);
    }

    @Override
    public void setDispatcher(ParseDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        this.parseMacrosDispatcher.setDispatcher(dispatcher);
    }
}
