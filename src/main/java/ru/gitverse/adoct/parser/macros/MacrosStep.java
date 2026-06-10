package ru.gitverse.adoct.parser.macros;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.doc.ParseContext;

import java.util.Map;

public class MacrosStep extends AbstractParseMacros {
    public MacrosStep() {
        super("ui-steps", "ui-step");

    }

    @Override
    public void parse(String name, Map<String, String> parameter, Element body, ParseContext parseContext) {
        printer.println();
        // ui-steps — контейнер: просто разворачиваем вложенные ui-step (каждый рендерит себя сам).
        if (name.equalsIgnoreCase("ui-steps")) {
            dispatcher.parse(body.children(), parseContext);
            return;
        }
        // ui-step — отдельный шаг: разделитель + содержимое.
        printer.println("---");
        dispatcher.parse(body.children(), parseContext);
        printer.println();
    }
}
