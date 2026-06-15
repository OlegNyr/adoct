package ru.gitverse.adoct.parser.build.tag;

import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.build.BuildContext;
import ru.gitverse.adoct.parser.build.InlineBuilder;

import java.util.List;

/** Теги {@code <h1>}–{@code <h6>} → заголовки секций (уровень = цифра тега). */
public final class HeadingTag implements NodeTag {

    private final InlineBuilder inline;

    public HeadingTag(InlineBuilder inline) {
        this.inline = inline;
    }

    @Override
    public List<String> tags() {
        return List.of("h1", "h2", "h3", "h4", "h5", "h6");
    }

    @Override
    public List<Block> build(Element el, BuildContext ctx) {
        int level = el.nodeName().charAt(1) - '0';
        return List.of(new Block.Heading(level, inline.build(el, ctx)));
    }
}
