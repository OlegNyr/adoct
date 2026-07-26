package io.github.adoct.parser.build.macro;

import org.jsoup.nodes.Element;
import io.github.adoct.parser.ast.Block;
import io.github.adoct.parser.build.BlockBuilder;
import io.github.adoct.parser.build.BuildContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Макрос {@code toc} → {@code toc::[]}. */
public final class TocMacro extends AbstractNodeMacro {

    public TocMacro(BlockBuilder blocks) {
        super(blocks);
    }

    @Override
    public Set<String> names() {
        return Set.of("toc");
    }

    @Override
    public List<Block> build(String name, Map<String, String> params, Element body, BuildContext ctx) {
        return List.of(new Block.Toc());
    }
}
