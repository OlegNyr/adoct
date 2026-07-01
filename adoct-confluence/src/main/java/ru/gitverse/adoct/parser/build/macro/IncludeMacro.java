package ru.gitverse.adoct.parser.build.macro;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.PageFolder;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.ast.Inline;
import ru.gitverse.adoct.parser.build.BlockBuilder;
import ru.gitverse.adoct.parser.build.BuildContext;
import ru.gitverse.adoct.parser.build.LinkRenderer;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Трансклюзия страницы:
 * <ul>
 *   <li>{@code include} (Include Page) — вставляет страницу целиком → директива
 *       {@code include::<sanitize(заголовок)>/index.adoc[]}. Путь резолвится по конвенции экспорта:
 *       дочерние страницы лежат в подпапке {@code <заголовок>/} рядом с текущей. Работает, когда
 *       включаемая страница входит в выгружаемое поддерево (типичный кейс); для страниц вне поддерева
 *       (общие сниппеты в другой ветке/пространстве) директива будет висящей — ограничение
 *       статического экспорта.</li>
 *   <li>{@code excerpt-include} — вставляет только помеченный фрагмент, не всю страницу; полноценный
 *       {@code include::} исказил бы смысл, поэтому даём ссылку на исходную страницу.</li>
 * </ul>
 * Целевая страница лежит в безымянном {@code ac:parameter} как {@code <ac:link><ri:page/></ac:link>}.
 */
public final class IncludeMacro extends AbstractNodeMacro {

    public IncludeMacro(BlockBuilder blocks) {
        super(blocks);
    }

    @Override
    public Set<String> names() {
        return Set.of("include", "excerpt-include");
    }

    @Override
    public List<Block> build(String name, Map<String, String> params, Element body, BuildContext ctx) {
        Element link = params.values().stream()
                .map(html -> Jsoup.parse(html).getElementsByTag("ac:link").first())
                .filter(el -> el != null)
                .findFirst()
                .orElse(null);
        if (link == null) {
            return List.of();
        }
        if ("include".equals(name == null ? "" : name.toLowerCase(Locale.ROOT))) {
            return includeDirective(link, ctx);
        }
        return pageLink(link, ctx);
    }

    /** {@code include} → include::-директива по имени папки страницы (или ссылка, если нет заголовка). */
    private List<Block> includeDirective(Element link, BuildContext ctx) {
        String title = link.getElementsByTag("ri:page").attr("ri:content-title");
        if (StringUtils.isBlank(title)) {
            return pageLink(link, ctx);
        }
        String directive = "include::%s/index.adoc[]".formatted(PageFolder.sanitize(title));
        return List.of(new Block.RawBlock(directive));
    }

    /** Ссылка на исходную страницу (для excerpt-include и как fallback). */
    private List<Block> pageLink(Element link, BuildContext ctx) {
        String rendered = LinkRenderer.render(link, ctx.metadata());
        if (rendered.isEmpty()) {
            return List.of();
        }
        return List.of(new Block.Paragraph(List.of(new Inline.Raw(rendered))));
    }
}
