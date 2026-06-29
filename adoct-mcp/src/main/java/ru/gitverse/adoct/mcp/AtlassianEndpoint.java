package ru.gitverse.adoct.mcp;

/**
 * Точка подключения к Atlassian (Jira / Confluence / Bitbucket) Server/Data Center: базовый адрес,
 * PAT-токен, тип сервиса и признак «по умолчанию». Хранилище токенов остаётся в слое плагина — модуль
 * MCP получает готовые значения через {@link EndpointSupplier}.
 *
 * @param host    базовый URL (scheme + host), например {@code https://confluence.example.com}
 * @param token   Personal Access Token (Bearer)
 * @param kind    тип сервиса — на какой класс тулов этот хост отвечает по умолчанию
 * @param primary этот хост используется по умолчанию для своего {@link #kind} (когда {@code host} не задан)
 */
public record AtlassianEndpoint(String host, String token, AtlassianKind kind, boolean primary) {

    /** Совместимый конструктор без явного типа — тип определяется по хосту, не «по умолчанию». */
    public AtlassianEndpoint(String host, String token) {
        this(host, token, AtlassianKind.detect(host), false);
    }
}
