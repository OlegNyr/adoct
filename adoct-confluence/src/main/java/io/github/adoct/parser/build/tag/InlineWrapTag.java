package io.github.adoct.parser.build.tag;

import org.jsoup.nodes.Element;
import io.github.adoct.parser.ast.Block;
import io.github.adoct.parser.build.BuildContext;
import io.github.adoct.parser.build.InlineBuilder;

import java.util.List;

/** Блок-уровневые инлайн-теги ({@code <time>}, {@code ac:inline-comment-marker}) → абзац из их инлайна. */
public final class InlineWrapTag implements NodeTag {

    private final InlineBuilder inline;

    public InlineWrapTag(InlineBuilder inline) {
        this.inline = inline;
    }

    @Override
    public List<String> tags() {
        return List.of("time", "ac:inline-comment-marker");
    }

    @Override
    public List<Block> build(Element el, BuildContext ctx) {
        return NodeTag.paragraph(inline.buildOne(el, ctx));
    }
}
