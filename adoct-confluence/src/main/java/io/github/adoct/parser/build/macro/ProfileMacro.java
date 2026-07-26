package io.github.adoct.parser.build.macro;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import io.github.adoct.parser.ast.Block;
import io.github.adoct.parser.ast.Inline;
import io.github.adoct.parser.build.BlockBuilder;
import io.github.adoct.parser.build.BuildContext;
import io.github.adoct.parser.build.LinkRenderer;

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
        List<Inline> mention = LinkRenderer.user(userKey, ctx.metadata());
        if (mention.isEmpty()) {
            return List.of();
        }
        return List.of(new Block.Paragraph(mention));
    }
}
