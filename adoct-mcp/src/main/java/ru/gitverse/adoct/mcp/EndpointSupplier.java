package ru.gitverse.adoct.mcp;

import java.util.List;
import java.util.Optional;

/**
 * Поставщик доступов к Atlassian для тулов MCP. Это шов, отвязывающий модуль от способа хранения
 * токенов (в плагине — IntelliJ-настройки; в тестах — фейк со списком).
 */
public interface EndpointSupplier {

    /** Все сконфигурированные точки подключения. */
    List<AtlassianEndpoint> all();

    /** Точка по умолчанию — первая сконфигурированная (если есть). */
    default Optional<AtlassianEndpoint> defaultEndpoint() {
        return all().stream().findFirst();
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
