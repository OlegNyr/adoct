package ru.gitverse.adoct.mcp;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Поставщик доступов к Atlassian для тулов MCP. Это шов, отвязывающий модуль от способа хранения
 * токенов (в плагине — IntelliJ-настройки; в тестах — фейк со списком).
 */
public interface EndpointSupplier {

    /** Все сконфигурированные точки подключения. */
    List<AtlassianEndpoint> all();

    /**
     * Включённые группы инструментов по типу сервиса. Тул показывается, только если его группа
     * (по префиксу имени {@code jira_}/{@code confluence_}/{@code bitbucket_}) здесь присутствует.
     * По умолчанию — все группы (CLI/тесты получают полный набор).
     */
    default Set<AtlassianKind> enabledToolGroups() {
        return EnumSet.allOf(AtlassianKind.class);
    }

    /** Проект Jira по умолчанию (когда инсталляция «однопроектная») — подставляется, если не задан в аргументах. */
    default Optional<String> defaultJiraProject() {
        return Optional.empty();
    }

    /** Пространство Confluence по умолчанию — подставляется, если не задано в аргументах. */
    default Optional<String> defaultConfluenceSpace() {
        return Optional.empty();
    }

    /** Ростер команды (для привязки задач к людям). */
    default List<TeamMember> team() {
        return List.of();
    }

    /** Конфигурация типов задач: шаблон + диаграмма состояний на каждый тип (свободный текст для LLM). */
    default List<Template> templates() {
        return List.of();
    }

    /** Точка по умолчанию — первая сконфигурированная (если есть). */
    default Optional<AtlassianEndpoint> defaultEndpoint() {
        return all().stream().findFirst();
    }

    /**
     * Точка по умолчанию для типа сервиса: сначала помеченная {@code primary} нужного {@code kind},
     * затем любая того же {@code kind}, и лишь как запасной вариант — первая вообще (чтобы старые
     * одно-хостовые конфигурации продолжали работать). Так {@code jira_*} уходит на Jira-хост, а не на
     * первый попавшийся (например Confluence).
     */
    default Optional<AtlassianEndpoint> defaultEndpoint(AtlassianKind kind) {
        List<AtlassianEndpoint> all = all();
        return all.stream().filter(e -> e.kind() == kind && e.primary()).findFirst()
                .or(() -> all.stream().filter(e -> e.kind() == kind).findFirst())
                .or(() -> all.stream().findFirst());
    }

    /**
     * Подбор точки по хосту из аргумента тула. Пустой {@code host} → {@link #defaultEndpoint()}.
     * Сравнение по «authority» без учёта схемы/завершающего слэша и регистра.
     */
    default Optional<AtlassianEndpoint> forHost(String host) {
        if (host == null || host.isBlank()) {
            return defaultEndpoint();
        }
        String wanted = authority(host);
        return all().stream()
                .filter(e -> authority(e.host()).equals(wanted))
                .findFirst();
    }

    /** Нормализует адрес к виду {@code host[:port]} в нижнем регистре (без схемы, пути и слэшей). */
    static String authority(String value) {
        if (value == null) {
            return "";
        }
        String s = value.trim().toLowerCase();
        int scheme = s.indexOf("://");
        if (scheme >= 0) {
            s = s.substring(scheme + 3);
        }
        int slash = s.indexOf('/');
        if (slash >= 0) {
            s = s.substring(0, slash);
        }
        return s;
    }
}
