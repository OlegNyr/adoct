package ru.gitverse.adoct.parser.build.tag;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.build.BlockBuilder;
import ru.gitverse.adoct.parser.build.BuildContext;
import ru.gitverse.adoct.parser.build.InlineBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Теги {@code <ul>}/{@code <ol>} → {@link Block.ItemList}. Текст элемента — инлайн; вложенные списки
 * становятся дочерними блоками (ItemList), вложенная таблица диспетчеризуется через {@link BlockBuilder}.
 */
public final class ListTag implements NodeTag {

    private final BlockBuilder blocks;
    private final InlineBuilder inline;

    public ListTag(BlockBuilder blocks, InlineBuilder inline) {
        this.blocks = blocks;
        this.inline = inline;
    }

    @Override
    public List<String> tags() {
        return List.of("ul", "ol");
    }

    @Override
    public List<Block> build(Element el, BuildContext ctx) {
        return List.of(list(el, ctx));
    }

    private Block.ItemList list(Element list, BuildContext ctx) {
        boolean ordered = list.nodeName().equalsIgnoreCase("ol");
        List<Block.ListItem> items = new ArrayList<>();
        for (Element li : list.children()) {
            if (li.nodeName().equalsIgnoreCase("li")) {
                items.add(listItem(li, ctx));
            }
        }
        return new Block.ItemList(ordered, items);
    }

    private Block.ListItem listItem(Element li, BuildContext ctx) {
        List<Node> inlineNodes = new ArrayList<>();
        List<Block> children = new ArrayList<>();
        for (Node node : li.childNodes()) {
            String n = node.nodeName().toLowerCase();
            if ((n.equals("ul") || n.equals("ol")) && node instanceof Element nested) {
                children.add(list(nested, ctx));
            } else if (n.equals("table") && node instanceof Element nested) {
                children.addAll(blocks.buildOne(nested, ctx));
            } else {
                inlineNodes.add(node);
            }
        }
        return new Block.ListItem(inline.buildNodes(inlineNodes, ctx), children);
    }
}
