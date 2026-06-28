package ru.gitverse.adoct.parser.build.macro;

import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.build.BlockBuilder;
import ru.gitverse.adoct.parser.build.BuildContext;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Confluence-admonition-макросы → AsciiDoc admonition.
 * {@code note}/{@code info} → {@code [NOTE]}, {@code tip} → {@code [TIP]}, {@code warning} → {@code [WARNING]}.
 */
public final class NoteMacro extends AbstractNodeMacro {

    public NoteMacro(BlockBuilder blocks) {
        super(blocks);
    }

    @Override
    public Set<String> names() {
        return Set.of("note", "warning", "info", "tip");
    }

    @Override
    public List<Block> build(String name, Map<String, String> params, Element body, BuildContext ctx) {
        String kind = switch (name == null ? "" : name.toLowerCase(Locale.ROOT)) {
            case "warning" -> "WARNING";
            case "tip" -> "TIP";
            default -> "NOTE";
        };
        return List.of(new Block.Admonition(kind, blankToNull(params.get("title")), children(body, ctx)));
    }
}
