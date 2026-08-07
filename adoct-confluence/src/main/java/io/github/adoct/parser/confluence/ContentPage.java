package io.github.adoct.parser.confluence;

import java.util.List;
import java.util.Map;

/**
 * Загруженная страница Confluence. {@code date} — дата последней версии ({@code version.when},
 * трактуется как «изменена»); {@code space}/{@code createdDate}/{@code author} — доп. метаданные для
 * заголовка/frontmatter (могут быть {@code null}, если не запрошены/недоступны).
 *
 * <p>{@code labels} различает три состояния: {@code null} — метки не запрашивались (нужен отдельный
 * REST-вызов), пустой список — запрошены и меток нет, непустой — метки страницы. Различие важно:
 * метки едут в основном запросе через {@code expand=metadata.labels}, и по пустому списку нельзя
 * понять, надо ли ходить за ними отдельно.
 */
public record ContentPage(String title, String url, String date,
                          String content,
                          String view,
                          Map<String, LinkResult> attachment,
                          String space, String createdDate, String author,
                          List<String> labels) {

    /** Совместимый конструктор без меток (метки будут запрошены отдельно). */
    public ContentPage(String title, String url, String date, String content, String view,
                       Map<String, LinkResult> attachment,
                       String space, String createdDate, String author) {
        this(title, url, date, content, view, attachment, space, createdDate, author, null);
    }

    /** Совместимый конструктор без доп. метаданных (для in-memory/тестов). */
    public ContentPage(String title, String url, String date, String content, String view,
                       Map<String, LinkResult> attachment) {
        this(title, url, date, content, view, attachment, null, null, null, null);
    }
}

