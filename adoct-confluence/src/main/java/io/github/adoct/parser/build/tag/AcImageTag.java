package io.github.adoct.parser.build.tag;

import org.jsoup.nodes.Element;
import io.github.adoct.parser.ast.Block;
import io.github.adoct.parser.build.BuildContext;
import io.github.adoct.parser.build.ImageRenderer;

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
