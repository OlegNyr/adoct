package ru.gitverse.adoct.parser.macros;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.doc.ParseContext;

import java.util.Map;

public class MacrosAnchor extends AbstractParseMacros {
    public MacrosAnchor() {
        super("anchor");
    }

    @Override
    public void parse(String name, Map<String, String> parameter, Element body, ParseContext parseContext) {
        // Имя якоря Confluence кладёт в безымянный параметр (ac:name=""); на всякий случай
        // берём первый непустой параметр как запасной вариант. Пустой якорь не печатаем.
        String anchor = parameter.get("");
        if (StringUtils.isBlank(anchor)) {
            anchor = parameter.values().stream().filter(StringUtils::isNotBlank).findFirst().orElse(null);
        }
        if (StringUtils.isBlank(anchor)) {
            return;
        }
        printer.println();
        printer.println("[#%s]".formatted(anchor));
    }
}
