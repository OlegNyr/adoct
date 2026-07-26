package io.github.adoct.parser.build.tag;

import org.jsoup.nodes.Element;
import io.github.adoct.parser.ast.Block;
import io.github.adoct.parser.ast.Inline;
import io.github.adoct.parser.build.BuildContext;
import io.github.adoct.parser.build.ImageRenderer;

import java.util.List;

/** Блок-уровневый {@code <img>} → картинка блочного уровня (файл копируется из выгрузки в папку картинок). */
public final class ImgTag implements NodeTag {

    private final ImageRenderer imageRenderer;

    public ImgTag(ImageRenderer imageRenderer) {
        this.imageRenderer = imageRenderer;
    }

    @Override
    public List<String> tags() {
        return List.of("img");
    }

    @Override
    public List<Block> build(Element el, BuildContext ctx) {
        Inline.Image img = imageRenderer.img(el, el.text().trim());
        return List.of(new Block.Image(img.target(), img.alt(), img.width(), img.height(), null));
    }
}
