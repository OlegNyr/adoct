package io.github.adoct.parser.build.macro;

import org.jsoup.nodes.Element;
import io.github.adoct.parser.ast.Block;
import io.github.adoct.parser.ast.Inline;
import io.github.adoct.parser.build.BlockBuilder;
import io.github.adoct.parser.build.BuildContext;
import io.github.adoct.parser.confluence.LinkResult;
import io.github.adoct.parser.model.MetadataKey;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Макрос {@code children} (Children Display) — динамический список дочерних страниц. Статически
 * воспроизводим его как маркированный список ссылок на прямые дочерние страницы выгрузки (локальные
 * пути на их {@code index.<ext>}). Список готовит {@link io.github.adoct.parser.DispatcherPage} в
 * {@link MetadataKey#CHILDREN}; вне древовидной выгрузки (in-memory/одна страница) — макрос пуст.
 */
public final class ChildrenMacro extends AbstractNodeMacro {

    public ChildrenMacro(BlockBuilder blocks) {
        super(blocks);
    }

    @Override
    public Set<String> names() {
        return Set.of("children");
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Block> build(String name, Map<String, String> params, Element body, BuildContext ctx) {
        Object children = ctx.metadata().get(MetadataKey.CHILDREN);
        if (!(children instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Block.ListItem> items = ((List<LinkResult>) children).stream()
                .map(c -> new Block.ListItem(
                        List.of(new Inline.Link(c.url(), List.of(new Inline.Text(c.title())))), List.of()))
                .toList();
        return List.of(new Block.ItemList(false, items));
    }
}
