package ru.gitverse.adoct.parser.build.macro;

import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.build.BlockBuilder;
import ru.gitverse.adoct.parser.build.BuildContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Confluence-панель → sidebar-блок {@code ****} с опц. заголовком; тело строится рекурсивно. */
public final class PanelMacro extends AbstractNodeMacro {

    public PanelMacro(BlockBuilder blocks) {
        super(blocks);
    }

    @Override
    public Set<String> names() {
        return Set.of("panel");
    }

    @Override
    public List<Block> build(String name, Map<String, String> params, Element body, BuildContext ctx) {
        return List.of(new Block.Sidebar(blankToNull(params.get("title")), children(body, ctx)));
    }
}
