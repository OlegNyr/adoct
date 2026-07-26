package io.github.adoct.parser.build.tag;

import org.jsoup.nodes.Element;
import io.github.adoct.parser.ast.Block;
import io.github.adoct.parser.ast.Inline;
import io.github.adoct.parser.build.BuildContext;

import java.util.List;

/**
 * Обработчик одного HTML-тега storage-формата — строит блоки AST. Аналог старого {@code ParseTags},
 * но возвращает {@link Block} вместо печати. Реализации регистрируются по {@link #tags()} в
 * {@link io.github.adoct.parser.build.BlockBuilder}; при совпадении тега берётся первый, у кого
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
     * Абзац из инлайна с одной оговоркой: одиночная инлайн-картинка повышается до блочной.
     * Общий хелпер для тегов, дающих абзац (p, ac:link, time, comment-marker).
     */
    static List<Block> paragraph(List<Inline> inline) {
        if (inline.isEmpty()) {
            return List.of();
        }
        if (inline.size() == 1 && inline.getFirst() instanceof Inline.Image im) {
            return List.of(new Block.Image(im.target(), im.alt(), im.width(), im.height(), null));
        }
        return List.of(new Block.Paragraph(inline));
    }
}
