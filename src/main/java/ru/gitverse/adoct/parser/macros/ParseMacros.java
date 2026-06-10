package ru.gitverse.adoct.parser.macros;

import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.PrintWriterReturn;
import ru.gitverse.adoct.parser.doc.ParseContext;
import ru.gitverse.adoct.parser.doc.ParseDispatcher;

import java.util.Map;
import java.util.Set;

public interface ParseMacros {
    Set<String> getMacrosName();

    void setPrinter(PrintWriterReturn printer);

    void setDispatcher(ParseDispatcher dispatcher);

    void parse(String name, Map<String, String> parameter, Element body, ParseContext parseContext);
}
