package ru.gitverse.adoct.mcp;

/**
 * Точка подключения к Atlassian (Jira или Confluence) Server/Data Center: базовый адрес и PAT-токен.
 * Хранилище токенов остаётся в слое плагина — модуль MCP получает готовые значения через {@link EndpointSupplier}.
 *
 * @param host  базовый URL (scheme + host), например {@code https://confluence.example.com}
 * @param token Personal Access Token (Bearer)
 */
public record AtlassianEndpoint(String host, String token) {
}
