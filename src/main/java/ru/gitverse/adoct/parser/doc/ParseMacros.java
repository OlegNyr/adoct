package ru.gitverse.adoct.parser.doc;

import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import ru.gitverse.adoct.parser.PrintWriterReturn;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// NOTE: легаси/мёртвый код. В ConvertStorageToAdoc используется ParseTagMacros + ParseMacrosDispatcher,
//       а не этот класс (обрабатывает только "ui-expand"). Кандидат на удаление.
public class ParseMacros implements ParseTags, SetterDispatcher {
    @Setter
    PrintWriterReturn printWriter;
    @Setter
    ParseDispatcher dispatcher;

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
                    .orElse(null);
            selectMacros(name, parameter, body, parseContext);
        }
    }

    private void selectMacros(String name, Map<String, String> parameter, Element body, ParseContext parseContext) {
        if ("ui-expand".equals(name)) {
            printWriter.println();
            printWriter.println("== "+parameter.get("title"));
            printWriter.println();
            dispatcher.parse(body.children(), parseContext);
            printWriter.println();
        } else {
            //ignore
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


}
