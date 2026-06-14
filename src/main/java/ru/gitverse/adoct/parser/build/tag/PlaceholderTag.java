package ru.gitverse.adoct.parser.build.tag;

import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.build.BuildContext;

import java.util.List;

/** {@code <ac:placeholder>} — выкидываем (заглушка пустого редактора Confluence). */
public final class PlaceholderTag implements NodeTag {

    @Override
    public List<String> tags() {
        return List.of("ac:placeholder");
    }

    @Override
    public List<Block> build(Element el, BuildContext ctx) {
        return List.of();
    }
}
