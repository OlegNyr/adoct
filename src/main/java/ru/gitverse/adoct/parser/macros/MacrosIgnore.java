package ru.gitverse.adoct.parser.macros;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.doc.ParseContext;

import java.util.Map;
import java.util.Set;

@Slf4j
public class MacrosIgnore extends AbstractParseMacros {
    public static MacrosIgnore INSTANCE = new MacrosIgnore();

    public MacrosIgnore() {
        super();
    }

    @Override
    public Set<String> getMacrosName() {
        return Set.of();
    }

    @Override
    public void parse(String name, Map<String, String> parameter, Element body, ParseContext parseContext) {
        log.warn("Ignore macros {} params {}", name, parameter);
    }
}
