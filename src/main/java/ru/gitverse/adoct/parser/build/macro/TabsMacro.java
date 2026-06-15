package ru.gitverse.adoct.parser.build.macro;

import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.build.BlockBuilder;
import ru.gitverse.adoct.parser.build.BuildContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Табы: {@code ui-tabs} — контейнер с разделителем {@code ---}; {@code ui-tab} — под-заголовок + тело. */
public final class TabsMacro extends AbstractNodeMacro {

    public TabsMacro(BlockBuilder blocks) {
        super(blocks);
    }

    @Override
    public Set<String> names() {
        return Set.of("ui-tabs", "ui-tab");
    }

    @Override
    public List<Block> build(String name, Map<String, String> params, Element body, BuildContext ctx) {
        if ("ui-tabs".equals(name)) {
            return prepend(new Block.RawBlock("---"), children(body, ctx));
        }
        return titled(params.get("title"), body, ctx);
    }
}
