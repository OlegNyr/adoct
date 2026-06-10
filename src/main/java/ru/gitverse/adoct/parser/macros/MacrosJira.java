package ru.gitverse.adoct.parser.macros;

import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.doc.ParseContext;

import java.util.Map;

public class MacrosJira extends AbstractParseMacros {
    public MacrosJira() {
        super("jira");
    }

    @Override
    public void parse(String name, Map<String, String> parameter, Element body, ParseContext parseContext) {
        if (!printer.isLastReturn()) {
            printer.print(" ");
        }
        printer.print("link:https://jira.example.com/browse/%s[]".formatted(parameter.get("key")));
    }
}
