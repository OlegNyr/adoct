package ru.gitverse.adoct.parser.build.tag;

import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.build.BuildContext;
import ru.gitverse.adoct.parser.build.ImageRenderer;

import java.util.List;

/** Блок-уровневый {@code <img>} → {@code image::} (файл копируется из выгрузки в папку картинок). */
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
        return List.of(new Block.RawBlock("image::" + imageRenderer.img(el, el.text().trim())));
    }
}
