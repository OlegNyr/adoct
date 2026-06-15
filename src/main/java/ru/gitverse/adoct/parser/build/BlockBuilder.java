package ru.gitverse.adoct.parser.build;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.ast.Inline;
import ru.gitverse.adoct.parser.build.tag.NodeTag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Диспетчер блок-тегов: реестр имя-тега→[{@link NodeTag}], первый подходящий ({@link NodeTag#isWork})
 * строит блоки. Неизвестный тег → абзац из {@code element.text()}. Уровень заголовка протягивается
 * явно по списку соседних блоков (замена мутабельного {@code topHeader}). Каждый тег — в своём файле
 * в пакете {@code build.tag}.
 */
public final class BlockBuilder {

    private Map<String, List<NodeTag>> byTag = Map.of();

    /** Регистрирует хендлеры тегов (вызывается из {@link AstBuilder} после конструирования). */
    public void setTags(List<NodeTag> tags) {
        Map<String, List<NodeTag>> map = new HashMap<>();
        for (NodeTag tag : tags) {
            for (String name : tag.tags()) {
                map.computeIfAbsent(name, k -> new ArrayList<>()).add(tag);
            }
        }
        this.byTag = map;
    }

    /** Строит блоки из последовательности элементов, протягивая уровень заголовка вперёд. */
    public List<Block> build(Elements elements, BuildContext ctx) {
        List<Block> out = new ArrayList<>();
        int level = ctx.headingLevel();
        for (Element el : elements) {
            out.addAll(dispatch(el, ctx.withHeadingLevel(level)));
            int hl = headingLevelOf(el);
            if (hl > 0) {
                level = hl;
            }
        }
        return out;
    }

    /** Диспетчеризует одиночный элемент (без протягивания уровня заголовка). */
    public List<Block> buildOne(Element el, BuildContext ctx) {
        return dispatch(el, ctx);
    }

    private List<Block> dispatch(Element el, BuildContext ctx) {
        List<NodeTag> handlers = byTag.get(el.nodeName().toLowerCase());
        if (handlers != null) {
            for (NodeTag handler : handlers) {
                if (handler.isWork(el)) {
                    return handler.build(el, ctx);
                }
            }
        }
        String text = el.text();
        return text.isBlank() ? List.of() : List.of(new Block.Paragraph(List.of(new Inline.Text(text))));
    }

    private static int headingLevelOf(Element el) {
        String n = el.nodeName();
        if (n.length() == 2 && n.charAt(0) == 'h' && Character.isDigit(n.charAt(1))) {
            return n.charAt(1) - '0';
        }
        return 0;
    }
}
