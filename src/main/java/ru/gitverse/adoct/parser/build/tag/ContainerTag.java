package ru.gitverse.adoct.parser.build.tag;

import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.build.BlockBuilder;
import ru.gitverse.adoct.parser.build.BuildContext;

import java.util.List;

/** Контейнеры без собственной разметки ({@code <div>}, секции, {@code ac:layout*}) — рекурсия в детей. */
public final class ContainerTag implements NodeTag {

    private final BlockBuilder blocks;

    public ContainerTag(BlockBuilder blocks) {
        this.blocks = blocks;
    }

    @Override
    public List<String> tags() {
        return List.of("div", "ac:layout", "ac:layout-section", "ac:layout-cell");
    }

    @Override
    public List<Block> build(Element el, BuildContext ctx) {
        return List.of(new Block.Container(blocks.build(el.children(), ctx)));
    }
}
