package ru.gitverse.adoct.parser.build.tag;

import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.build.BuildContext;
import ru.gitverse.adoct.parser.build.MacroBuilder;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** {@code <ac:structured-macro>} → делегирует в реестр макросов {@link MacroBuilder}. */
public final class MacroTag implements NodeTag {

    private final MacroBuilder macros;

    public MacroTag(MacroBuilder macros) {
        this.macros = macros;
    }

    @Override
    public List<String> tags() {
        return List.of("ac:structured-macro");
    }

    @Override
    public List<Block> build(Element el, BuildContext ctx) {
        String name = el.attr("ac:name");
        Map<String, String> params = el.children().stream()
                .filter(e -> e.nodeName().equals("ac:parameter"))
                .collect(Collectors.toMap(e -> e.attr("ac:name"), Element::html, (a, b) -> a));
        Element body = childByName(el, "ac:rich-text-body")
                .or(() -> childByName(el, "ac:plain-text-body"))
                .orElse(null);
        return macros.build(name, params, body, ctx);
    }

    private static java.util.Optional<Element> childByName(Element el, String tag) {
        return el.children().stream().filter(e -> e.nodeName().equals(tag)).findFirst();
    }
}
