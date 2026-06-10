package ru.gitverse.adoct.parser.macros;

import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.PrintWriterReturn;
import ru.gitverse.adoct.parser.doc.ParseContext;
import ru.gitverse.adoct.parser.doc.ParseDispatcher;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ParseMacrosDispatcher {
    private final Map<String, ParseMacros> macrosMap;

    public ParseMacrosDispatcher(List<ParseMacros> parserMacros) {
        macrosMap = parserMacros
                .stream()
                .flatMap(p -> p
                        .getMacrosName()
                        .stream()
                        .map(name -> new PairMacros(name, p))
                )
                .collect(Collectors.toMap(PairMacros::name, PairMacros::macros));
    }

    public void setPrinter(PrintWriterReturn printer) {
        this.macrosMap.values().forEach(e -> e.setPrinter(printer));
    }

    public void setDispatcher(ParseDispatcher dispatcher) {
        this.macrosMap.values().forEach(e -> e.setDispatcher(dispatcher));
    }

    public void parse(String name, Map<String, String> parameter, Element body, ParseContext parseContext) {
        ParseMacros parseMacros = macrosMap.getOrDefault(name, MacrosIgnore.INSTANCE);
        parseMacros.parse(name, parameter, body, parseContext);
    }

    private record PairMacros(String name, ParseMacros macros) {
    }
}
