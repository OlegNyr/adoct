package ru.gitverse.adoct.parser.macros;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.doc.ParseContext;

import java.util.Map;

public class MacrosNumber extends AbstractParseMacros {
    public MacrosNumber() {
        super("numberedheadings");
    }

    @Override
    public void parse(String name, Map<String, String> parameter, Element body, ParseContext parseContext) {
        printer.println();
        printer.println(":sectnums:");
        String numer = parameter.get("start-numbering-with");
        if (StringUtils.isNotEmpty(numer)) {
            printer.println(":sectnumslevels: %s".formatted(numer));
        }
        dispatcher.parse(body.children(), parseContext);
        printer.println();
    }
}
