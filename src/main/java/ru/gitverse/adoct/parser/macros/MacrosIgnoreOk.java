package ru.gitverse.adoct.parser.macros;

import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.doc.ParseContext;

import java.util.Map;

public class MacrosIgnoreOk extends AbstractParseMacros {
    public MacrosIgnoreOk() {
        super("expandall");
    }

    @Override
    public void parse(String name, Map<String, String> parameter, Element body, ParseContext parseContext) {

    }
}
