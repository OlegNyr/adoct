package ru.gitverse.adoct.parser.macros;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.doc.ParseContext;

import java.util.Map;

public class MacrosExpand extends AbstractParseMacros {
    public MacrosExpand() {
        super("ui-expand", "expand", "excerpt", "details");

    }

    @Override
    public void parse(String name, Map<String, String> parameter, Element body, ParseContext parseContext) {
        printer.println();
        String title = parameter.get("title");
        if (StringUtils.isNotEmpty(title)) {
            printer.printHeader(parseContext.getTopHeader() + 1);
            printer.print(' ');
            printer.println(title);
        }
        printer.println();
        dispatcher.parse(body.children(), parseContext);
        printer.println();
    }
}
