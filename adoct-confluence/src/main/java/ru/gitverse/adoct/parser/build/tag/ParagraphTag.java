package ru.gitverse.adoct.parser.build.tag;

import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.build.BuildContext;
import ru.gitverse.adoct.parser.build.InlineBuilder;

import java.util.List;

/** Тег {@code <p>} → абзац. */
public final class ParagraphTag implements NodeTag {

    private final InlineBuilder inline;

    public ParagraphTag(InlineBuilder inline) {
        this.inline = inline;
    }

    @Override
    public List<String> tags() {
        return List.of("p");
    }

    @Override
    public List<Block> build(Element el, BuildContext ctx) {
        return NodeTag.paragraph(inline.build(el, ctx));
    }
}
