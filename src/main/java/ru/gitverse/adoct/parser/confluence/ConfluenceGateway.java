package ru.gitverse.adoct.parser.confluence;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Подмножество API Confluence, которое требуется оркестратору {@link ru.gitverse.adoct.parser.DispatcherPage}.
 * <p>
 * Выделено из {@link ConfluenceClient}, чтобы оркестрацию (резолв ссылок, кэш, ветвление splitting)
 * можно было покрыть тестами с фейковой реализацией — без сети и живого Confluence.
 */
public interface ConfluenceGateway {

    /** Загружает основную страницу со storage/view-телом и картой вложений. */
    ContentPage getMainPage(String id);

    /** Ищет страницу по заголовку (и опционально ключу пространства). */
    List<LinkResult> search(String title, String key);

    /** Резолвит пользователя по ключу в ссылку на его профиль. */
    LinkResult user(String userKey);

    /** Скачивает вложения в указанный каталог, дёргая {@code progress} на каждый файл. */
    void loadAttach(Collection<LinkResult> values, Path attachmentFolder, Consumer<String> progress);
}
