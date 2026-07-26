package io.github.adoct.parser.confluence;

import java.util.Map;

/**
 * Загруженная страница Confluence. {@code date} — дата последней версии ({@code version.when},
 * трактуется как «изменена»); {@code space}/{@code createdDate}/{@code author} — доп. метаданные для
 * заголовка/frontmatter (могут быть {@code null}, если не запрошены/недоступны).
 */
public record ContentPage(String title, String url, String date,
                          String content,
                          String view,
                          Map<String, LinkResult> attachment,
                          String space, String createdDate, String author) {

    /** Совместимый конструктор без доп. метаданных (для in-memory/тестов). */
    public ContentPage(String title, String url, String date, String content, String view,
                       Map<String, LinkResult> attachment) {
        this(title, url, date, content, view, attachment, null, null, null);
    }
}

