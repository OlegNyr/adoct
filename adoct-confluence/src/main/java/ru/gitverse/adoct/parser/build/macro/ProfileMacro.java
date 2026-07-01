package ru.gitverse.adoct.parser.build.macro;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.ast.Inline;
import ru.gitverse.adoct.parser.build.BlockBuilder;
import ru.gitverse.adoct.parser.build.BuildContext;
import ru.gitverse.adoct.parser.build.LinkRenderer;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Макрос упоминания пользователя {@code profile} на блочном уровне → абзац со ссылкой на пользователя.
 * Inline-вхождения (внутри {@code <p>}/ячеек) обрабатывает {@code InlineBuilder}; здесь — редкий случай
 * прямого потомка блока. Пустой пользователь (аноним) не даёт вывода.
 */
public final class ProfileMacro extends AbstractNodeMacro {

    public ProfileMacro(BlockBuilder blocks) {
        super(blocks);
    }

    @Override
    public Set<String> names() {
        return Set.of("profile");
    }

    @Override
    public List<Block> build(String name, Map<String, String> params, Element body, BuildContext ctx) {
        String userKey = Jsoup.parse(params.getOrDefault("user", ""))
                .getElementsByTag("ri:user").attr("ri:userkey");
        String mention = LinkRenderer.user(userKey, ctx.metadata());
        if (StringUtils.isEmpty(mention)) {
            return List.of();
        }
        return List.of(new Block.Paragraph(List.of(new Inline.Raw(mention))));
    }
}
