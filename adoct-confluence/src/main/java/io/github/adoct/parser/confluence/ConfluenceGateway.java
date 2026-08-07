package io.github.adoct.parser.confluence;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Подмножество API Confluence, которое требуется оркестратору {@link io.github.adoct.parser.DispatcherPage}.
 * <p>
 * Выделено из {@link ConfluenceClient}, чтобы оркестрацию (резолв ссылок, кэш, ветвление splitting)
 * можно было покрыть тестами с фейковой реализацией — без сети и живого Confluence.
 */
public interface ConfluenceGateway {

    /** Загружает основную страницу со storage/view-телом и картой вложений. */
    ContentPage getMainPage(String id);

    /** ID дочерних страниц (прямых потомков) данной страницы, в порядке Confluence. */
    List<String> getChildPageIds(String id);

    /** Ссылка на страницу поддерева: ID + заголовок (для карты дерева при межстраничных ссылках). */
    record PageRef(String id, String title) {
    }

    /**
     * Дочерние страницы с заголовками (id + title) — для построения карты дерева. По умолчанию —
     * из {@link #getChildPageIds} без заголовков (тогда локальные перекрёстные ссылки недоступны).
     */
    default List<PageRef> childPages(String id) {
        return getChildPageIds(id).stream().map(childId -> new PageRef(childId, null)).toList();
    }

    /** Корневые страницы пространства (верхний уровень дерева) — для выгрузки пространства целиком. */
    default List<PageRef> spaceRootPages(String spaceKey) {
        return List.of();
    }

    /**
     * Узел дерева страниц: id, заголовок, id родителя ({@code null} — страница верхнего уровня) и
     * версия ({@code version.when}). Плоский список таких узлов однозначно задаёт дерево.
     *
     * <p>{@code version} позволяет отсеять неизменившиеся страницы <b>до</b> загрузки их тела;
     * {@code null} — версия неизвестна, страницу придётся загрузить и сверить как раньше.
     */
    record PageNode(String id, String title, String parentId, String version) {

        /** Узел с неизвестной версией. */
        public PageNode(String id, String title, String parentId) {
            this(id, title, parentId, null);
        }
    }

    /**
     * Все страницы пространства <b>одним пакетом</b> (пагинация внутри) — чтобы не обходить дерево
     * запросом на страницу. Пустой список означает «не поддерживается / не получилось»: вызывающий
     * обязан откатиться на рекурсивный обход через {@link #childPages}.
     */
    default List<PageNode> spaceTree(String spaceKey) {
        return List.of();
    }

    /**
     * Все потомки страницы (на любой глубине) одним пакетом. Сама страница {@code rootId} в выдачу
     * не входит. Пустой список — как и в {@link #spaceTree}, сигнал откатиться на обход по одному.
     */
    default List<PageNode> subtree(String rootId) {
        return List.of();
    }

    /** Ищет страницу по заголовку (и опционально ключу пространства). */
    List<LinkResult> search(String title, String key);

    /**
     * Базовый URL Confluence ({@code https://host}) — чтобы достроить до абсолютных ссылки, которые
     * rendered view отдаёт относительными ({@code /pages/viewpage.action?pageId=…}). По умолчанию
     * пусто: реализация без сети такие ссылки не достраивает.
     */
    default String baseUrl() {
        return "";
    }

    /** Резолвит пользователя по ключу в ссылку на его профиль. */
    LinkResult user(String userKey);

    /** Скачивает вложения в указанный каталог, дёргая {@code progress} на каждый файл. */
    void loadAttach(Collection<LinkResult> values, Path attachmentFolder, Consumer<String> progress);

    /**
     * Метки (labels) страницы для экспорта в {@code :keywords:} (adoc) / {@code tags} (md). По умолчанию
     * пусто — реализация best-effort, чтобы отсутствие меток или ошибка REST не роняли экспорт.
     */
    default List<String> labels(String id) {
        return List.of();
    }
}
