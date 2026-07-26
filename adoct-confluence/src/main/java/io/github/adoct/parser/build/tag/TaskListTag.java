package io.github.adoct.parser.build.tag;

import org.jsoup.nodes.Element;
import io.github.adoct.parser.ast.Block;
import io.github.adoct.parser.build.BuildContext;
import io.github.adoct.parser.build.InlineBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Тег {@code <ac:task-list>} → {@link Block.TaskList} (чекбоксы). Статус {@code complete} → отмечен.
 * Формат-нейтрально: AsciiDoc даёт {@code * [x]}, Markdown — {@code - [x]}.
 */
public final class TaskListTag implements NodeTag {

    private final InlineBuilder inline;

    public TaskListTag(InlineBuilder inline) {
        this.inline = inline;
    }

    @Override
    public List<String> tags() {
        return List.of("ac:task-list");
    }

    @Override
    public List<Block> build(Element el, BuildContext ctx) {
        List<Block.TaskItem> items = new ArrayList<>();
        for (Element task : el.getElementsByTag("ac:task")) {
            boolean checked = "complete".equalsIgnoreCase(task.getElementsByTag("ac:task-status").text());
            Element body = task.getElementsByTag("ac:task-body").first();
            items.add(new Block.TaskItem(checked, body == null ? List.of() : inline.build(body, ctx)));
        }
        return items.isEmpty() ? List.of() : List.of(new Block.TaskList(items));
    }
}
