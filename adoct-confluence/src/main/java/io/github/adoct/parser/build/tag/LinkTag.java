package io.github.adoct.parser.build.tag;

import org.jsoup.nodes.Element;
import io.github.adoct.parser.ast.Block;
import io.github.adoct.parser.build.BuildContext;
import io.github.adoct.parser.build.InlineBuilder;

import java.util.List;

/** Блок-уровневый {@code <ac:link>} → абзац со ссылкой (резолв внутри {@link InlineBuilder}). */
public final class LinkTag implements NodeTag {

    private final InlineBuilder inline;

    public LinkTag(InlineBuilder inline) {
        this.inline = inline;
    }

    @Override
    public List<String> tags() {
        return List.of("ac:link");
    }

    @Override
    public List<Block> build(Element el, BuildContext ctx) {
        return NodeTag.paragraph(inline.buildOne(el, ctx));
    }
}
