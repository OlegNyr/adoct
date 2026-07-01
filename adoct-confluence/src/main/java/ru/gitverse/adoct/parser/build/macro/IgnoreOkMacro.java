package ru.gitverse.adoct.parser.build.macro;

import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.build.BlockBuilder;
import ru.gitverse.adoct.parser.build.BuildContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Макросы, которые намеренно ничего не выводят: служебная обёртка {@code expandall} и динамические
 * серверные листинги ({@code children}, {@code detailssummary}, {@code contentbylabel},
 * {@code attachments}) — их контент Confluence собирает на лету, статического эквивалента нет.
 * Регистрация здесь (а не «unknown») подавляет предупреждающий лог: это осознанный пропуск.
 */
public final class IgnoreOkMacro extends AbstractNodeMacro {

    public IgnoreOkMacro(BlockBuilder blocks) {
        super(blocks);
    }

    @Override
    public Set<String> names() {
        return Set.of("expandall", "children", "detailssummary", "contentbylabel", "attachments");
    }

    @Override
    public List<Block> build(String name, Map<String, String> params, Element body, BuildContext ctx) {
        return List.of();
    }
}
