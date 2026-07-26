package io.github.adoct.parser.ast;

import java.util.List;

/**
 * Инлайн-узел (содержимое абзаца, ячейки, заголовка, элемента списка) — формат-нейтральный.
 * <p>
 * Дерево вместо «сырого текста»: форматирование строится структурно, а финальную разметку
 * (жирный/курсив/ссылка/картинка) собирает конкретный writer ({@link AsciiDocWriter} /
 * {@link MarkdownWriter}). {@link Text} хранит СЫРОЕ значение — экранирование ({@code |} в ячейках,
 * {@code ]} в подписях ссылок и т.п.) делает writer под свой формат.
 */
public sealed interface Inline {

    /** Простой текст (сырой, без экранирования). */
    record Text(String value) implements Inline {
    }

    /** Жирный. */
    record Bold(List<Inline> children) implements Inline {
    }

    /** Курсив. */
    record Italic(List<Inline> children) implements Inline {
    }

    /** Подчёркивание. */
    record Underline(List<Inline> children) implements Inline {
    }

    /** Моноширинный (inline-код). */
    record Mono(List<Inline> children) implements Inline {
    }

    /** Цвет текста (экспортируется только при включённом color-режиме). */
    record Colored(String color, List<Inline> children) implements Inline {
    }

    /** Ссылка: {@code url} + подпись (может быть пустой — тогда показывается сам {@code url}). */
    record Link(String url, List<Inline> label) implements Inline {
    }

    /** Внутренняя перекрёстная ссылка (якорь): AsciiDoc {@code <<anchor, text>>} / Markdown {@code [text](#anchor)}. */
    record CrossRef(String anchor, String text) implements Inline {
    }

    /** Инлайн-картинка. */
    record Image(String target, String alt, String width, String height) implements Inline {
    }

    /** Статус-лозенг Confluence: цвет + подпись. */
    record Status(String colour, String title) implements Inline {
    }

    /** Перенос строки внутри абзаца. */
    record LineBreak() implements Inline {
    }
}
