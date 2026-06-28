package ru.gitverse.adoct.parser.build.tag;

import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.build.BuildContext;
import ru.gitverse.adoct.parser.build.ImageRenderer;

import java.util.List;

/** Блок-уровневый {@code <ac:image>} → {@code image::} (файл не копируется). */
public final class AcImageTag implements NodeTag {

    @Override
    public List<String> tags() {
        return List.of("ac:image");
    }

    @Override
    public List<Block> build(Element el, BuildContext ctx) {
        return List.of(new Block.RawBlock("image::" + ImageRenderer.acImage(el, el.text().trim())));
    }
}
