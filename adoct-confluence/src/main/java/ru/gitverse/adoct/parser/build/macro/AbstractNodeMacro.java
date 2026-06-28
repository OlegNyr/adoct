package ru.gitverse.adoct.parser.build.macro;

import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.ast.Inline;
import ru.gitverse.adoct.parser.build.BlockBuilder;
import ru.gitverse.adoct.parser.build.BuildContext;
import ru.gitverse.adoct.parser.model.MetadataKey;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * База для {@link NodeMacro}: хранит {@link BlockBuilder} для рекурсии по телу и общие хелперы
 * (под-заголовок, вынос длинного текста во внешний файл и т.п.).
 */
public abstract class AbstractNodeMacro implements NodeMacro {

    protected final BlockBuilder blocks;

    protected AbstractNodeMacro(BlockBuilder blocks) {
        this.blocks = blocks;
    }

    /** Рекурсивно строит блоки тела макроса (пустой список, если тела нет). */
    protected List<Block> children(Element body, BuildContext ctx) {
        return body == null ? List.of() : blocks.build(body.children(), ctx);
    }

    /** Под-заголовок из title (уровень = текущий + 1) + тело. Для expand/ui-tab. */
    protected List<Block> titled(String title, Element body, BuildContext ctx) {
        List<Block> out = new ArrayList<>();
        if (StringUtils.isNotEmpty(title)) {
            out.add(new Block.Heading(ctx.headingLevel() + 1, List.of(new Inline.Text(title))));
        }
        out.addAll(children(body, ctx));
        return out;
    }

    /**
     * Длинный текст (> {@code maxIncludeString} строк) выносит в файл и заменяет на {@code include::}.
     * В in-memory режиме ({@link MetadataKey#IN_MEMORY}) файл не пишется — текст инлайнится как есть.
     */
    @SneakyThrows
    protected String externalize(String text, BuildContext ctx, String fileName) {
        if (Boolean.TRUE.equals(ctx.metadata().get(MetadataKey.IN_MEMORY))
                || StringUtils.countMatches(text, "\n") <= ctx.maxIncludeString()) {
            return text;
        }
        Path filesFolder = (Path) ctx.metadata().getOrDefault(MetadataKey.FILES_FOLDER,
                ctx.metadata().get(MetadataKey.DESTINATION_FOLDER));
        Files.writeString(filesFolder.resolve(fileName), text);
        Object folderName = ctx.metadata().get(MetadataKey.FILES_FOLDER_NAME);
        String include = folderName == null ? fileName : folderName + "/" + fileName;
        return "include::%s[]".formatted(include);
    }

    protected static List<Block> prepend(Block first, List<Block> rest) {
        List<Block> out = new ArrayList<>();
        out.add(first);
        out.addAll(rest);
        return out;
    }

    protected static String blankToNull(String s) {
        return StringUtils.isBlank(s) || "null".equals(s) ? null : s;
    }
}
