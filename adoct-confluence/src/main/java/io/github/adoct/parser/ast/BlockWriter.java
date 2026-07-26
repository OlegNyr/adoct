package io.github.adoct.parser.ast;

import java.util.List;

/** Сериализатор дерева {@link Block}/{@link Inline} в конкретный текстовый формат (AsciiDoc/Markdown). */
public interface BlockWriter {

    /** Превращает список блоков документа в текст целевого формата. */
    String write(List<Block> blocks);
}
