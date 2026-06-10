package ru.gitverse.adoct.parser.doc;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import ru.gitverse.adoct.parser.PrintWriterReturn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ParseDispatcher {

    private final Map<String, List<ParseTags>> parseTag;
    private final PrintWriterReturn print;
    private final ParseTags paragraph;

    public ParseDispatcher(PrintWriterReturn writer, List<ParseTags> parseTagsList) {
        this.print = writer;
        Map<String, List<ParseTags>> parseTag = new HashMap<>();
        parseTagsList.forEach(p -> {
            if (p instanceof SetterDispatcher d) {
                d.setDispatcher(this);
            }
            p.setPrintWriter(writer);
            p.tags().forEach(t -> {
                parseTag.compute(t, (key, old) -> {
                    if (old == null) {
                        old = new ArrayList<>();
                    }
                    old.add(p);
                    return old;
                });
            });
        });
        this.parseTag = Map.copyOf(parseTag);
        this.paragraph = parseTag.get("p").stream().findFirst()
                .orElseThrow(() -> new RuntimeException("not found paragraph"));
    }


    public void parse(Elements elements, ParseContext parseContext) {
        for (Element element : elements) {
            parse(element, parseContext);
        }
    }

    public void parseText(Node element, ParseContext parseContext) {
        paragraph.parse(element, parseContext);
    }

    public void parse(Element element, ParseContext parseContext) {
        String name = element.nodeName();
        if (hasTag(name)) {

            Optional<ParseTags> parseTags = findParse(element, parseTag.get(name));
            if (parseTags.isPresent()) {
                parseTags.get().parse(element, parseContext);
            } else {
                //Если контейнер, то уходим на рекурсию
                if (name.equalsIgnoreCase("div")) {
                    this.parse(element.children(), parseContext);
                } else {
                    defaultWorkElement(element, parseContext);
                }
            }
        } else {
            defaultWorkElement(element, parseContext);
        }
    }

    public boolean hasTag(String name) {
        return parseTag.containsKey(name);
    }

    private void defaultWorkElement(Element element, ParseContext parseContext) {
        //Сохраняем текст
        print.println(element.text());
    }

    private Optional<ParseTags> findParse(Element element, List<ParseTags> parseTagsList) {
        return parseTagsList.stream().filter(p -> p.isWork(element)).findFirst();
    }

}
