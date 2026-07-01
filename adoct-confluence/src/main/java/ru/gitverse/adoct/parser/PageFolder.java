package ru.gitverse.adoct.parser;

/**
 * Имя папки/файла экспортированной страницы, полученное из её заголовка.
 * Дочерние страницы кладутся в подпапку {@code <родитель>/<sanitize(заголовок)>/}, поэтому та же
 * функция используется при резолве пути для {@code include::} (макрос Include Page).
 */
public final class PageFolder {

    private PageFolder() {
    }

    /** Заменяет недопустимые в ФС символы на {@code _}; пустой результат → {@code page}. */
    public static String sanitize(String title) {
        String trimmed = title == null ? "" : title.strip();
        String safe = trimmed.replaceAll("[<>:\"/\\\\|?*\\x00-\\x1F]", "_").replaceAll("[. ]+$", "");
        return safe.isBlank() ? "page" : safe;
    }
}
