package ru.gitverse.adoct.parser.ast;

import java.util.List;

/**
 * Инлайн-узел AsciiDoc (содержимое абзаца, ячейки, заголовка, элемента списка).
 * <p>
 * Дерево вместо «сырого текста»: форматирование строится структурно, а финальную разметку
 * (** , __ , [.role]## , link:…) собирает {@link AsciiDocWriter}. Это убирает зависимость от
 * состояния writer'а ({@code isLastReturn}) и пост-обработку строк.
 */
public sealed interface Inline {

    /** Простой текст (уже с экранированием '|' для таблиц, если нужно). */
    record Text(String value) implements Inline {
    }

    /** Жирный: {@code **…**}. */
    record Bold(List<Inline> children) implements Inline {
    }

    /** Курсив: {@code __…__}. */
    record Italic(List<Inline> children) implements Inline {
    }

    /** Подчёркивание: {@code [.underline]##…##}. */
    record Underline(List<Inline> children) implements Inline {
    }

    /** Моноширинный (inline-код): {@code `…`}. */
    record Mono(List<Inline> children) implements Inline {
    }

    /** Цвет: {@code [.<color>]##…##} (экспортируется только при включённом color-режиме). */
    record Colored(String color, List<Inline> children) implements Inline {
    }

    /** Ссылка: {@code link:url[label]}. */
    record Link(String url, List<Inline> label) implements Inline {
    }

    /** Перенос строки внутри абзаца. */
    record LineBreak() implements Inline {
    }

    /** Готовый инлайн-AsciiDoc, вставляемый как есть (резолв ссылок, jira и т.п.). */
    record Raw(String adoc) implements Inline {
    }
}
