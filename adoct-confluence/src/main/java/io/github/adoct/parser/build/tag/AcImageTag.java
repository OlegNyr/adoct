package io.github.adoct.parser.build.tag;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import io.github.adoct.parser.ast.Block;
import io.github.adoct.parser.ast.Inline;
import io.github.adoct.parser.build.BuildContext;
import io.github.adoct.parser.build.ImageRenderer;

import java.util.List;

/** Блок-уровневый {@code <ac:image>} → картинка блочного уровня (файл не копируется), с опц. подписью. */
public final class AcImageTag implements NodeTag {

    @Override
    public List<String> tags() {
        return List.of("ac:image");
    }

    @Override
    public List<Block> build(Element el, BuildContext ctx) {
        String caption = el.getElementsByTag("ac:caption").text();
        // текст ac:image это и есть подпись — чтобы не задваивать, alt берём только без подписи
        String alt = StringUtils.isBlank(caption) ? el.text().trim() : "";
        Inline.Image img = ImageRenderer.acImage(el, alt);
        return List.of(new Block.Image(img.target(), img.alt(), img.width(), img.height(),
                StringUtils.isBlank(caption) ? null : caption));
    }
}
