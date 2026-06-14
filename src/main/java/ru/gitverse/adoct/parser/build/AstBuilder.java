package ru.gitverse.adoct.parser.build;

import org.jsoup.select.Elements;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.build.tag.AcImageTag;
import ru.gitverse.adoct.parser.build.tag.ContainerTag;
import ru.gitverse.adoct.parser.build.tag.HeadingTag;
import ru.gitverse.adoct.parser.build.tag.ImgTag;
import ru.gitverse.adoct.parser.build.tag.InlineWrapTag;
import ru.gitverse.adoct.parser.build.tag.LinkTag;
import ru.gitverse.adoct.parser.build.tag.ListTag;
import ru.gitverse.adoct.parser.build.tag.MacroTag;
import ru.gitverse.adoct.parser.build.tag.ParagraphTag;
import ru.gitverse.adoct.parser.build.tag.PlaceholderTag;
import ru.gitverse.adoct.parser.build.tag.TableTag;
import ru.gitverse.adoct.parser.model.MetadataKey;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Фасад построения AST из storage-HTML: связывает inline/block/macro-строители и рендерер картинок.
 * Результат ({@code List<Block>}) дальше превращает в текст {@link ru.gitverse.adoct.parser.ast.AsciiDocWriter}.
 */
public final class AstBuilder {

    private final BlockBuilder blockBuilder;

    /**
     * @param attachmentSource каталог скачанных вложений (источник для копий {@code <img>})
     * @param imagesDest       каталог картинок назначения (куда копировать {@code <img>})
     */
    public AstBuilder(Path attachmentSource, Path imagesDest) {
        ImageRenderer imageRenderer = new ImageRenderer(attachmentSource, imagesDest);
        InlineBuilder inline = new InlineBuilder(imageRenderer);
        BlockBuilder block = new BlockBuilder();
        MacroBuilder macro = new MacroBuilder(block);
        block.setTags(List.of(
                new ParagraphTag(inline),
                new HeadingTag(inline),
                new TableTag(block, inline),
                new ListTag(block, inline),
                new ContainerTag(block),
                new MacroTag(macro),
                new LinkTag(inline),
                new AcImageTag(),
                new ImgTag(imageRenderer),
                new InlineWrapTag(inline),
                new PlaceholderTag()
        ));
        this.blockBuilder = block;
    }

    public List<Block> build(Elements body, Map<MetadataKey, Object> metadata, boolean workColor) {
        return blockBuilder.build(body, BuildContext.root(metadata, workColor));
    }
}
