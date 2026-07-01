package ru.gitverse.adoct.parser.build.macro;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.ast.Inline;
import ru.gitverse.adoct.parser.build.BlockBuilder;
import ru.gitverse.adoct.parser.build.BuildContext;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Статус-лозенг {@code status} → инлайн-лейбл с ролью цвета: {@code [.status-green]#On track#}.
 * Обычно встречается инлайн (обрабатывается в {@code InlineBuilder}); блочное вхождение даёт абзац.
 */
public final class StatusMacro extends AbstractNodeMacro {

    public StatusMacro(BlockBuilder blocks) {
        super(blocks);
    }

    @Override
    public Set<String> names() {
        return Set.of("status");
    }

    @Override
    public List<Block> build(String name, Map<String, String> params, Element body, BuildContext ctx) {
        String rendered = render(params);
        if (rendered.isEmpty()) {
            return List.of();
        }
        return List.of(new Block.Paragraph(List.of(new Inline.Raw(rendered))));
    }

    /** Инлайн-AsciiDoc лозенга: {@code [.status-<colour>]#<title>#}. Пустой title и цвет → пусто. */
    public static String render(Map<String, String> params) {
        String colour = blankToNull(params.get("colour"));
        String title = blankToNull(params.get("title"));
        String text = title != null ? title : colour;
        if (StringUtils.isBlank(text)) {
            return "";
        }
        String role = colour != null ? "status-" + colour.toLowerCase(Locale.ROOT) : "status";
        return "[.%s]#%s#".formatted(role, text);
    }
}
