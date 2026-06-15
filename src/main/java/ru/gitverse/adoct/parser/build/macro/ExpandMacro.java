package ru.gitverse.adoct.parser.build.macro;

import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.build.BlockBuilder;
import ru.gitverse.adoct.parser.build.BuildContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Раскрывающиеся блоки → под-заголовок из title (уровень текущий+1) + тело. */
public final class ExpandMacro extends AbstractNodeMacro {

    public ExpandMacro(BlockBuilder blocks) {
        super(blocks);
    }

    @Override
    public Set<String> names() {
        return Set.of("ui-expand", "expand", "excerpt", "details");
    }

    @Override
    public List<Block> build(String name, Map<String, String> params, Element body, BuildContext ctx) {
        return titled(params.get("title"), body, ctx);
    }
}
