package ru.gitverse.adoct.parser.build.tag;

import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.ast.Inline;
import ru.gitverse.adoct.parser.build.BuildContext;

import java.util.List;

/**
 * Обработчик одного HTML-тега storage-формата — строит блоки AST. Аналог старого {@code ParseTags},
 * но возвращает {@link Block} вместо печати. Реализации регистрируются по {@link #tags()} в
 * {@link ru.gitverse.adoct.parser.build.BlockBuilder}; при совпадении тега берётся первый, у кого
 * {@link #isWork(Element)} вернул {@code true}.
 */
public interface NodeTag {

    /** Имена тегов (в нижнем регистре), которые обрабатывает этот хендлер. */
    List<String> tags();

    /** Уточняющий предикат, когда один тег делят несколько хендлеров (по умолчанию — всегда). */
    default boolean isWork(Element element) {
        return true;
    }

    List<Block> build(Element element, BuildContext ctx);

    /**
     * Абзац из инлайна с одной оговоркой: одиночная инлайн-картинка повышается до блочной {@code image::}.
     * Общий хелпер для тегов, дающих абзац (p, ac:link, time, comment-marker).
     */
    static List<Block> paragraph(List<Inline> inline) {
        if (inline.isEmpty()) {
            return List.of();
        }
        if (inline.size() == 1 && inline.getFirst() instanceof Inline.Raw r
            && r.adoc().startsWith("image:") && !r.adoc().startsWith("image::")) {
            return List.of(new Block.RawBlock("image::" + r.adoc().substring("image:".length())));
        }
        return List.of(new Block.Paragraph(inline));
    }
}
