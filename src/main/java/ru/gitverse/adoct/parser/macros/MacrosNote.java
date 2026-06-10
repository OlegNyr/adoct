package ru.gitverse.adoct.parser.macros;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.doc.ParseContext;

import java.util.Map;

public class MacrosNote extends AbstractParseMacros {
    public MacrosNote() {
        super("note", "warning", "info");
    }

    @Override
    public void parse(String name, Map<String, String> parameter, Element body, ParseContext parseContext) {
        printer.println();
        String action = mapNote(name);

        printer.println("[%s]".formatted(action));
        String title = parameter.get("title");
        if (StringUtils.isNotEmpty(title)) {
            printer.println(".%s".formatted(title));
        }
        printer.println("====");
        dispatcher.parse(body.children(), parseContext);
        printer.println("====");
    }

    private String mapNote(String name) {
        if ("info".equalsIgnoreCase(name) || "note".equalsIgnoreCase(name)) {
            return "NOTE";
        }
        if ("warning".equalsIgnoreCase(name)) {
            return "WARNING";
        }
        // запасной вариант на случай нового имени макроса — чтобы не получить "[null]"
        return "NOTE";
    }
}