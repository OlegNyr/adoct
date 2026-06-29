package ru.gitverse.adoct.mcp;

/**
 * Тип точки подключения Atlassian. Определяет, какие тулы по умолчанию идут на этот хост:
 * {@code jira_*} → {@link #JIRA}, {@code confluence_*} → {@link #CONFLUENCE}, {@code bitbucket_*}
 * (на будущее) → {@link #BITBUCKET}. Нужен, чтобы при нескольких настроенных хостах запрос без явного
 * {@code host} уходил на правильный сервис (иначе Jira-вызов мог уйти на хост Confluence и получить 404).
 */
public enum AtlassianKind {
    JIRA,
    CONFLUENCE,
    BITBUCKET;

    /**
     * Эвристически определяет тип по адресу хоста (по подстроке в имени). Неопознанный хост считается
     * {@link #CONFLUENCE} (исторически основной сервис плагина) — в настройках тип можно переопределить.
     */
    public static AtlassianKind detect(String host) {
        String h = host == null ? "" : host.toLowerCase();
        if (h.contains("bitbucket") || h.contains("stash")) {
            return BITBUCKET;
        }
        if (h.contains("jira")) {
            return JIRA;
        }
        return CONFLUENCE;
    }

    /** Разбирает имя типа (регистронезависимо); при пустом/неизвестном — {@code fallback}. */
    public static AtlassianKind parse(String name, AtlassianKind fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
