package io.github.adoct.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import io.github.adoct.parser.confluence.ConfluenceGateway;
import io.github.adoct.parser.confluence.ContentPage;
import io.github.adoct.parser.confluence.LinkResult;
import io.github.adoct.parser.model.LinksAttachment;
import io.github.adoct.parser.model.LinksPage;
import io.github.adoct.parser.model.LinksUser;
import io.github.adoct.parser.model.LinksValue;
import io.github.adoct.parser.model.LocalPages;
import io.github.adoct.parser.model.MetadataKey;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public class DispatcherPage {
    private final ConfluenceGateway client;
    private final Path basePath;
    private final ObjectMapper objectMapper;
    @Setter
    private boolean exportColors;
    /**
     * Отладочный режим: сохранять папку {@code source/} (сырой storage/view, content.json и кэш
     * links.json). По умolчанию выключено — в обычном экспорте эта папка не нужна.
     */
    @Setter
    private boolean debug;
    /** Выгружать ли поддерево дочерних страниц (рекурсивно). По умолчанию да. */
    @Setter
    private boolean includeChildren = true;
    /** Скачивать ли вложения (файлы, картинки) в {@code attache/}. По умолчанию да. */
    @Setter
    private boolean includeAttachments = true;
    /**
     * Качать только те вложения, на которые тело страницы реально ссылается (см.
     * {@link ConvertStorageToAdoc#usedAttachments()}), а не все вложения страницы. По умолчанию
     * выключено — прежнее поведение.
     * <p>
     * Работает только при включённом {@link #includeAttachments}: если вложения не качаются вовсе,
     * фильтровать нечего.
     */
    @Setter
    private boolean onlyUsedAttachments;
    /** Целевой формат экспорта (AsciiDoc по умолчанию). */
    @Setter
    private OutputFormat format = OutputFormat.ADOC;
    /**
     * Инкрементальная выгрузка: если версия страницы ({@code version.when}) совпадает с уже выгруженной
     * (из заголовка {@code index.<ext>}), страница не перевыгружается. По умолчанию включено. Дочерние
     * страницы всё равно обходятся — каждая проверяется отдельно.
     */
    @Setter
    private boolean skipUnchanged = true;
    /**
     * Межстраничные ссылки: ссылку на страницу, которая тоже входит в выгружаемое поддерево, делать
     * локальной (относительный путь на её {@code index.<ext>}), а не на URL Confluence. По умолчанию включено.
     */
    @Setter
    private boolean crossPageLinks = true;
    /**
     * Сколько страниц выгружать одновременно. {@code 1} — последовательно, как раньше. Страницы
     * независимы (своя папка, свой конвертер), а узкое место — сеть, поэтому по умолчанию несколько.
     */
    @Setter
    private int exportThreads = DEFAULT_EXPORT_THREADS;
    /**
     * Каталог выгрузки для баг-репорта при падении. При параллельной выгрузке это папка именно той
     * страницы, которая упала первой: общее «текущая страница» здесь бессмысленно — страниц в работе
     * несколько, и баг-репорт собрал бы содержимое случайной из них.
     * <p>
     * {@code null}, пока ни одна страница не начала выгружаться.
     */
    private volatile Path destination;
    /** Папка страницы, на которой выгрузка упала. Побеждает первая ошибка — она же и причина падения. */
    private final AtomicReference<Path> failedDestination = new AtomicReference<>();

    /**
     * Каталог для баг-репорта: папка упавшей страницы, если выгрузка упала, иначе — последняя
     * обработанная (при последовательной выгрузке это одно и то же).
     */
    public Path getDestination() {
        Path failed = failedDestination.get();
        return failed != null ? failed : destination;
    }

    /** Исполнитель параллельной выгрузки; {@code null} — последовательный режим. */
    private volatile ExecutorService exportExecutor;
    /** Ограничитель одновременно выгружаемых страниц — см. {@link #exportSelfWithSlot}. */
    private volatile Semaphore pageSlots;

    /** Степень параллелизма выгрузки по умолчанию. */
    private static final int DEFAULT_EXPORT_THREADS = 4;
    /** Сколько ждать завершения задач при остановке параллельной выгрузки. */
    private static final long SHUTDOWN_WAIT_SECONDS = 30L;

    /** Карта поддерева: заголовок страницы → её папка относительно корня выгрузки ({@link #basePath}). */
    private final Map<String, Path> pageFolders = new HashMap<>();
    /** То же поддерево, но по id страницы — ссылки в теле чаще всего идут именно по {@code pageId}. */
    private final Map<String, Path> pageIdFolders = new HashMap<>();
    /** Корневые страницы, загруженные в пред-проходе для карты дерева — переиспользуются в export. */
    private final Map<String, ContentPage> prefetched = new ConcurrentHashMap<>();
    /** Дерево выгрузки: id страницы → её прямые дети. Заполняется пред-проходом. */
    private final Map<String, List<ConfluenceGateway.PageRef>> childrenByParent = new HashMap<>();
    /**
     * Узлы дерева по id — заголовок и версия, известные ещё до загрузки страницы. Позволяют отсеять
     * неизменившуюся страницу, не скачивая её тело и вложения. Заполняется только пакетным пред-проходом.
     */
    private final Map<String, ConfluenceGateway.PageNode> nodesById = new HashMap<>();
    /**
     * Кэш резолва пользователей на весь прогон: один и тот же автор встречается на сотнях страниц, а
     * REST-вызов на него уходил каждый раз. Пустой {@link Optional} — «не нашли», тоже кэшируем,
     * иначе ненайденный пользователь стоил бы запроса на каждой странице.
     */
    private final Map<String, Optional<LinkResult>> userCache = new ConcurrentHashMap<>();
    /** То же для поиска страницы по заголовку: ключ — {@code заголовок + пространство}. */
    private final Map<String, Optional<LinkResult>> searchCache = new ConcurrentHashMap<>();
    /**
     * Пред-проход собрал полное дерево — значит {@link #export} берёт детей из {@link #childrenByParent},
     * а не запрашивает {@code childPages} на каждую страницу заново.
     */
    private boolean treeLoaded;

    /** Ищет записанную версию в заголовке ({@code :confluency-version:} adoc / {@code confluency-version:} md). */
    private static final Pattern VERSION_LINE = Pattern.compile("(?m)^:?confluency-version:\\s*(.+)$");

    /**
     * Выгружает страницу и всё её поддерево: каждая страница — в свою подпапку
     * {@code <родитель>/<заголовок дочерней>/}. Возвращает заголовок корневой страницы.
     */
    @SneakyThrows
    public String generate(String id, ProgressCallback progressCallback) {
        ProgressCallback progress = startExportPool(progressCallback);
        try {
            buildSiteMap(List.of(new ConfluenceGateway.PageRef(id, null)), null, progress);
            return export(id, basePath, progress);
        } finally {
            stopExportPool();
        }
    }

    /**
     * Выгружает пространство целиком: все его корневые страницы и их поддеревья (каждая корневая — в свою
     * подпапку {@code <заголовок>/} внутри каталога выгрузки). Возвращает ключ пространства.
     */
    @SneakyThrows
    public String generateSpace(String spaceKey, ProgressCallback progressCallback) {
        List<ConfluenceGateway.PageRef> roots = client.spaceRootPages(spaceKey);
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("В пространстве %s нет страниц (или нет доступа)".formatted(spaceKey));
        }
        ProgressCallback progress = startExportPool(progressCallback);
        try {
            buildSiteMap(roots, spaceKey, progress);
            exportChildren(roots, basePath, progress);
        } finally {
            stopExportPool();
        }
        return spaceKey;
    }

    /**
     * Готовит параллельную выгрузку и возвращает прогресс-колбэк, безопасный для вызова из нескольких
     * потоков (индикатор вызывающего этого не обязан уметь). При {@code exportThreads <= 1} —
     * последовательный режим и исходный колбэк без обёртки.
     * <p>
     * Потоки виртуальные: обход дерева рекурсивный, и родитель ждёт детей — на пуле обычных потоков
     * это упёрлось бы в его размер. Одновременную нагрузку ограничивает {@link #pageSlots}.
     */
    private ProgressCallback startExportPool(ProgressCallback progressCallback) {
        if (exportThreads <= 1) {
            exportExecutor = null;
            pageSlots = null;
            return progressCallback;
        }
        exportExecutor = Executors.newVirtualThreadPerTaskExecutor();
        pageSlots = new Semaphore(exportThreads);
        return (text, step) -> {
            synchronized (this) {
                progressCallback.next(text, step);
            }
        };
    }

    /**
     * Останавливает параллельную выгрузку. Штатно все задачи уже дождались в {@link #awaitAll}, но
     * закрытие подстраховано {@code shutdownNow} + ожиданием: вызывающий сразу после этого закрывает
     * HTTP-клиент, и ни одна задача не должна его пережить.
     */
    private void stopExportPool() {
        ExecutorService executor = exportExecutor;
        exportExecutor = null;
        pageSlots = null;
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("Задачи выгрузки не завершились за {} с после остановки", SHUTDOWN_WAIT_SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Пред-проход: строит дерево выгрузки — карту {@code заголовок → папка} (чтобы ссылку на входящую в
     * выгрузку страницу сделать локальной) и карту {@code id → прямые дети} (чтобы {@link #export} не
     * запрашивал {@code childPages} на каждую страницу).
     * <p>
     * Сначала пробуем забрать всё дерево пакетом ({@link ConfluenceGateway#spaceTree}/
     * {@link ConfluenceGateway#subtree} — десятки запросов на пространство). Если реализация этого не
     * умеет или запрос не удался, откатываемся на обход по одной странице.
     */
    private void buildSiteMap(List<ConfluenceGateway.PageRef> roots, String spaceKey,
                              ProgressCallback progressCallback) {
        pageFolders.clear();
        pageIdFolders.clear();
        prefetched.clear();
        childrenByParent.clear();
        nodesById.clear();
        userCache.clear();
        searchCache.clear();
        failedDestination.set(null);
        treeLoaded = false;
        // Ни локальные ссылки, ни обход потомков не нужны — дерево строить незачем.
        if (!crossPageLinks && !includeChildren) {
            return;
        }
        progressCallback.next("Строим карту дерева страниц", 0D);
        List<ConfluenceGateway.PageNode> nodes = loadTree(roots, spaceKey);
        if (!nodes.isEmpty()) {
            buildFromNodes(nodes, roots.stream().map(ConfluenceGateway.PageRef::id).toList());
            treeLoaded = true;
            progressCallback.next("Карта дерева готова: страниц %d".formatted(nodes.size()), 0D);
            return;
        }
        walkSiteMap(roots, progressCallback);
        treeLoaded = true;
        progressCallback.next("Карта дерева готова: страниц %d".formatted(pageIdFolders.size()), 0D);
    }

    /**
     * Плоский список всех страниц выгрузки, полученный пакетом. Пусто — режим не поддержан гейтвеем
     * или запрос не удался (тогда вызывающий обходит дерево по одной странице).
     */
    private List<ConfluenceGateway.PageNode> loadTree(List<ConfluenceGateway.PageRef> roots, String spaceKey) {
        if (spaceKey != null) {
            return client.spaceTree(spaceKey);
        }
        ConfluenceGateway.PageRef root = roots.getFirst();
        List<ConfluenceGateway.PageNode> descendants = client.subtree(root.id());
        if (descendants.isEmpty()) {
            return List.of();
        }
        // subtree() отдаёт только потомков — корень добавляем сами, его заголовок берём из страницы
        // (она же ляжет в prefetched и переиспользуется в export).
        List<ConfluenceGateway.PageNode> nodes = new ArrayList<>(descendants.size() + 1);
        nodes.add(new ConfluenceGateway.PageNode(root.id(), rootTitle(root), null));
        nodes.addAll(descendants);
        return nodes;
    }

    /**
     * Заголовок корня: из ссылки, если известен, иначе грузим страницу — один раз, придерживая её в
     * {@link #prefetched} для {@link #export} (и на случай повторного обращения из отката).
     */
    private String rootTitle(ConfluenceGateway.PageRef root) {
        if (root.title() != null) {
            return root.title();
        }
        return prefetched.computeIfAbsent(root.id(), client::getMainPage).title();
    }

    /**
     * Разворачивает плоский список узлов в карты папок и детей. Путь папки — цепочка заголовков от
     * корня.
     * <p>
     * В карту ссылок попадают только страницы, <b>достижимые от корней выгрузки</b>. Узел, чьего
     * родителя нет в выдаче (родитель закрыт правами или выдача оборвалась), экспортироваться не
     * будет — а если бы он попал в карту, ссылки на него стали бы локальными путями к файлам,
     * которых на диске нет. Такие узлы просто не резолвятся локально и остаются URL'ами Confluence.
     */
    private void buildFromNodes(List<ConfluenceGateway.PageNode> nodes, List<String> rootIds) {
        Map<String, ConfluenceGateway.PageNode> byId = new HashMap<>();
        for (ConfluenceGateway.PageNode node : nodes) {
            byId.putIfAbsent(node.id(), node);
        }
        nodesById.putAll(byId);
        for (ConfluenceGateway.PageNode node : nodes) {
            if (node.parentId() != null && byId.containsKey(node.parentId())) {
                childrenByParent.computeIfAbsent(node.parentId(), k -> new ArrayList<>())
                        .add(new ConfluenceGateway.PageRef(node.id(), node.title()));
            }
        }
        sortChildren();
        int reachable = mapReachableFolders(byId, rootIds);
        if (reachable < byId.size()) {
            // Не «тихое» усечение: пользователь должен видеть, что часть выдачи в выгрузку не войдёт.
            log.warn("В дереве {} страниц, достижимо от корней {} — остальные не выгружаются "
                    + "(родитель закрыт правами или выдача оборвалась)", byId.size(), reachable);
        }
    }

    /**
     * Обходит дерево от корней и раскладывает достижимые страницы по папкам. Возвращает их число —
     * расхождение с размером выдачи означает недостижимые узлы.
     */
    private int mapReachableFolders(Map<String, ConfluenceGateway.PageNode> byId, List<String> rootIds) {
        Deque<Map.Entry<String, Path>> queue = new ArrayDeque<>();
        for (String rootId : rootIds) {
            ConfluenceGateway.PageNode root = byId.get(rootId);
            if (root != null) {
                queue.add(Map.entry(rootId, Path.of(sanitizeFolderName(root.title()))));
            }
        }
        Set<String> visited = new HashSet<>();
        int reachable = 0;
        while (!queue.isEmpty()) {
            Map.Entry<String, Path> current = queue.poll();
            if (!visited.add(current.getKey())) {
                continue;
            }
            reachable++;
            ConfluenceGateway.PageNode node = byId.get(current.getKey());
            if (crossPageLinks) {
                pageFolders.putIfAbsent(node.title(), current.getValue());
                pageIdFolders.putIfAbsent(node.id(), current.getValue());
            }
            for (ConfluenceGateway.PageRef child : childrenByParent.getOrDefault(current.getKey(), List.of())) {
                queue.add(Map.entry(child.id(),
                        current.getValue().resolve(sanitizeFolderName(child.title()))));
            }
        }
        return reachable;
    }

    /**
     * Единый порядок дочерних страниц — по заголовку. Ни пакетная выдача, ни {@code childPages} не
     * дают устойчивого порядка между собой, а он виден в выводе (макрос {@code children}): без
     * сортировки выгрузка одной и той же страницы разными путями давала бы разный результат.
     */
    private void sortChildren() {
        Comparator<ConfluenceGateway.PageRef> byTitle =
                Comparator.comparing(ConfluenceGateway.PageRef::title, Comparator.nullsLast(String::compareTo));
        childrenByParent.values().forEach(list -> list.sort(byTitle));
    }

    /** Откат: обход дерева по одной странице ({@code childPages} на каждую) — как было до пакетной загрузки. */
    private void walkSiteMap(List<ConfluenceGateway.PageRef> roots, ProgressCallback progressCallback) {
        Set<String> visited = new HashSet<>();
        for (ConfluenceGateway.PageRef root : roots) {
            String title = rootTitle(root);
            Path rootFolder = Path.of(sanitizeFolderName(title));
            if (crossPageLinks) {
                pageFolders.put(title, rootFolder);
                pageIdFolders.put(root.id(), rootFolder);
            }
            walkSiteMap(root.id(), rootFolder, visited, progressCallback);
        }
        // Тот же порядок, что и у пакетного пути: иначе выгрузка через откат давала бы другой вывод.
        sortChildren();
    }

    private void walkSiteMap(String id, Path folder, Set<String> visited, ProgressCallback progressCallback) {
        if (!visited.add(id)) {
            return;
        }
        // В карту кладём детей как есть: для рекурсии нужен только id, заголовок может быть неизвестен
        // (тогда страница просто не попадёт в карту папок и в макрос children). Копия — потому что
        // гейтвей вправе вернуть неизменяемый список, а sortChildren() сортирует на месте.
        List<ConfluenceGateway.PageRef> children = new ArrayList<>(client.childPages(id));
        childrenByParent.put(id, children);
        progressCallback.next("Строим карту дерева страниц: %d".formatted(visited.size()), 0D);
        for (ConfluenceGateway.PageRef ref : children) {
            // Заголовок нужен только для карты папок; без него страница просто не участвует в
            // перекрёстных ссылках, но обойти её поддерево всё равно надо.
            if (ref.title() == null) {
                walkSiteMap(ref.id(), folder, visited, progressCallback);
                continue;
            }
            Path childFolder = folder.resolve(sanitizeFolderName(ref.title()));
            if (crossPageLinks) {
                pageFolders.putIfAbsent(ref.title(), childFolder);
                pageIdFolders.putIfAbsent(ref.id(), childFolder);
            }
            walkSiteMap(ref.id(), childFolder, visited, progressCallback);
        }
    }

    /** Прямые дети страницы: из дерева пред-прохода, иначе — запросом. */
    private List<ConfluenceGateway.PageRef> childrenOf(String id) {
        return treeLoaded ? childrenByParent.getOrDefault(id, List.of()) : client.childPages(id);
    }

    /**
     * Конвертирует одну страницу в AsciiDoc и возвращает строкой — полностью в памяти, без записи
     * файлов (дочерние страницы и вложения не выгружаются; ссылки резолвятся как при экспорте).
     */
    @SneakyThrows
    public String toAdoc(String id) {
        return toAdoc(id, client.getMainPage(id));
    }

    /** Как {@link #toAdoc(String)}, но по уже загруженной странице — без повторного REST-запроса. */
    public String toAdoc(String id, ContentPage mainPage) {
        return toAdoc(id, mainPage, false);
    }

    /**
     * Конвертация одной страницы в AsciiDoc по уже загруженной странице.
     *
     * @param fast {@code true} — быстрый режим для подачи контекста: ссылки резолвятся только локально
     *             (страницы — из rendered view, вложения — из метаданных; пользователи остаются нерезолв.),
     *             без дополнительных REST-запросов {@code search}/{@code user}. {@code false} — как при экспорте.
     */
    @SneakyThrows
    public String toAdoc(String id, ContentPage mainPage, boolean fast) {
        ConvertStorageToAdoc converter = new ConvertStorageToAdoc(mainPage.content(), mainPage.view());
        converter.setFormat(format);
        Map<String, String> resolveView = converter.resolveLink();
        Set<LinksValue> links = converter.getLinks();
        Map<LinksValue, LinkResult> linksResolvers = fast
                ? resolveLinksLocal(links, resolveView, mainPage.attachment())
                : getLinks(Map.of(), links, resolveView, mainPage.attachment(), null, null);

        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        Map<MetadataKey, Object> metadata = new HashMap<>();
        metadata.put(MetadataKey.LINKS, linksResolvers);
        metadata.put(MetadataKey.TITLE, mainPage.title());
        metadata.put(MetadataKey.PAGE_ID, id);
        metadata.put(MetadataKey.ATTACH_FOLDER, tmp);
        metadata.put(MetadataKey.ATTACH_FOLDER_NAME, "attache");
        metadata.put(MetadataKey.IMAGE, "attache");
        metadata.put(MetadataKey.DESTINATION_FOLDER, tmp);
        metadata.put(MetadataKey.FILES_FOLDER, tmp);
        metadata.put(MetadataKey.FILES_FOLDER_NAME, "files");
        metadata.put(MetadataKey.COLOR, exportColors);
        metadata.put(MetadataKey.IN_MEMORY, true);
        return converter.toAdoc(metadata, tmp);
    }

    /** Резолв ссылок без сети: страницы — из rendered view, вложения — из метаданных, пользователи — пропускаются. */
    private Map<LinksValue, LinkResult> resolveLinksLocal(Set<LinksValue> links, Map<String, String> resolveView,
                                                          Map<String, LinkResult> attachment) {
        Map<LinksValue, LinkResult> out = new HashMap<>();
        for (LinksValue link : links) {
            switch (link) {
                case LinksPage page -> {
                    String url = resolveView.get(page.title());
                    if (url != null) {
                        out.put(link, new LinkResult(page.title(), pageUrl(url, null)));
                    }
                }
                case LinksAttachment att -> {
                    LinkResult res = attachment.get(att.filename());
                    if (res != null) {
                        out.put(link, res);
                    }
                }
                case LinksUser ignored -> {
                    // профиль пользователя требует REST — в fast-режиме оставляем нерезолвленным
                }
                default -> {
                    // другие типы не ожидаются
                }
            }
        }
        return out;
    }

    /**
     * Выгружает страницу и её поддерево. Своя работа страницы и обход потомков разделены намеренно:
     * при параллельной выгрузке слот занимается только на свою работу и освобождается до ожидания
     * детей — иначе родители заняли бы все слоты, ожидая детей, которым слотов уже не досталось.
     */
    private String export(String id, Path baseDir, ProgressCallback progressCallback) {
        PageExport page = exportSelfWithSlot(id, baseDir, progressCallback);
        exportChildren(page.children(), page.destination(), progressCallback);
        return page.title();
    }

    /** Результат выгрузки самой страницы — без её потомков. */
    private record PageExport(String title, Path destination, List<ConfluenceGateway.PageRef> children) {
    }

    /** Выгружает саму страницу, заняв слот параллелизма (если параллельный режим включён). */
    private PageExport exportSelfWithSlot(String id, Path baseDir, ProgressCallback progressCallback) {
        Semaphore slots = pageSlots;
        if (slots == null) {
            return exportSelf(id, baseDir, progressCallback);
        }
        slots.acquireUninterruptibly();
        try {
            return exportSelf(id, baseDir, progressCallback);
        } finally {
            slots.release();
        }
    }

    @SneakyThrows
    private PageExport exportSelf(String id, Path baseDir, ProgressCallback progressCallback) {
        PageExport skipped = skipUnchangedWithoutLoading(id, baseDir, progressCallback);
        if (skipped != null) {
            return skipped;
        }

        progressCallback.next("Загрузка основной страницы", 0.2D);
        ContentPage mainPage = takePrefetched(id);
        Path destination = baseDir.resolve(sanitizeFolderName(mainPage.title()));
        this.destination = destination;
        List<ConfluenceGateway.PageRef> children = includeChildren ? childrenOf(id) : List.of();

        // Инкрементально: не изменившуюся страницу пропускаем, но детей всё равно обходим.
        if (skipUnchanged && isUnchanged(destination, mainPage.date())) {
            progressCallback.next("Пропуск (не изменилась): %s".formatted(mainPage.title()), 0.2D);
            return new PageExport(mainPage.title(), destination, children);
        }

        try {
            return writePage(id, mainPage, destination, children, progressCallback);
        } catch (RuntimeException | Error failure) {
            // Запоминаем папку именно этой страницы: при параллельной выгрузке к моменту, когда
            // ошибку поймает вызывающий, «текущей» уже будет чужая страница.
            failedDestination.compareAndSet(null, destination);
            throw failure;
        }
    }

    /** Пишет страницу на диск: вложения, резолв ссылок, конвертация. */
    @SneakyThrows
    private PageExport writePage(String id, ContentPage mainPage, Path destination,
                                 List<ConfluenceGateway.PageRef> children, ProgressCallback progressCallback) {
        Files.createDirectories(destination);
        ConvertStorageToAdoc converter = new ConvertStorageToAdoc(mainPage.content(), mainPage.view(), destination);
        converter.setFormat(format);

        Path source = destination.resolve("source");
        if (debug) {
            Files.createDirectories(source);
            Files.writeString(source.resolve("body.storage.html"), mainPage.content());
            Files.writeString(source.resolve("view.storage.html"), mainPage.view());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(source.resolve("content.json").toFile(), mainPage);
        }

        Path attachmentFolder = destination.resolve("attache");
        Files.createDirectories(attachmentFolder);

        if (includeAttachments) {
            List<LinkResult> countAttache = ownAttachments(converter, mainPage.attachment());
            // Картинки с чужих страниц по определению используются — фильтр к ним не применяем.
            countAttache.addAll(foreignImages(converter, mainPage.attachment()));
            client.loadAttach(countAttache, attachmentFolder,
                    filename ->
                            progressCallback.next("Загружаем вложение %s".formatted(filename),
                                    0.3D / countAttache.size()));
        }

        Map<LinksValue, LinkResult> loadLinksResolve = loadLinks(source);
        Map<String, String> resolveView = converter.resolveLink();
        Set<LinksValue> links = converter.getLinks();
        Path currentFolder = basePath.relativize(destination);
        Map<LinksValue, LinkResult> linksResolvers
                = getLinks(loadLinksResolve, links, resolveView, mainPage.attachment(), currentFolder,
                link ->
                        progressCallback.next("Резолвим ссылку %s".formatted(link), 0.2D / links.size())
        );
        if (debug) {
            saveLinks(source, linksResolvers);
        }

        progressCallback.next("Конвертируем страницу %s".formatted(mainPage.title()), 0.2D);

        Path filesDirectory = destination.resolve("files");
        if (Files.exists(filesDirectory)) {
            FileUtils.cleanDirectory(filesDirectory.toFile());
        } else {
            Files.createDirectories(filesDirectory);
        }

        Map<MetadataKey, Object> metadata = new HashMap<>();
        metadata.put(MetadataKey.LINKS, linksResolvers);
        metadata.put(MetadataKey.LOCAL_PAGES, localPages(currentFolder));
        metadata.put(MetadataKey.TITLE, mainPage.title());
        metadata.put(MetadataKey.PAGE_ID, id);
        metadata.put(MetadataKey.URL, mainPage.url());
        metadata.put(MetadataKey.CREATE, mainPage.date());
        putIfNotNull(metadata, MetadataKey.SPACE, mainPage.space());
        putIfNotNull(metadata, MetadataKey.AUTHOR, mainPage.author());
        putIfNotNull(metadata, MetadataKey.CREATED, mainPage.createdDate());
        metadata.put(MetadataKey.ATTACH_FOLDER, attachmentFolder);
        metadata.put(MetadataKey.ATTACH_FOLDER_NAME, "attache");
        metadata.put(MetadataKey.IMAGE, "attache");
        metadata.put(MetadataKey.DESTINATION_FOLDER, destination);
        metadata.put(MetadataKey.FILES_FOLDER, filesDirectory);
        metadata.put(MetadataKey.FILES_FOLDER_NAME, "files");
        metadata.put(MetadataKey.COLOR, exportColors);
        // Метки обычно приезжают в основном запросе (expand=metadata.labels); отдельный вызов —
        // только если реализация их не отдала (null, а не пустой список).
        metadata.put(MetadataKey.LABELS,
                mainPage.labels() != null ? mainPage.labels() : client.labels(id));
        metadata.put(MetadataKey.CHILDREN, childLinks(children));
        if (mainPage.date() != null) {
            metadata.put(MetadataKey.VERSION, mainPage.date());
        }
        converter.convert(metadata, attachmentFolder);

        // files/ и attache/ оставляем, только если в них что-то есть.
        deleteIfEmpty(filesDirectory);
        deleteIfEmpty(attachmentFolder);

        return new PageExport(mainPage.title(), destination, children);
    }

    /**
     * Вложения самой страницы, которые нужно скачать: все либо, при {@link #onlyUsedAttachments},
     * только те, на которые ссылается тело.
     * <p>
     * Фильтр сопоставляет по имени файла: вложение, которого нет в теле (старая версия документа,
     * забытый черновик, авто-превью), не качается. Ссылка на файл, которого нет во вложениях,
     * фильтром не создаётся — такой случай разбирает {@link #foreignImages}.
     */
    private List<LinkResult> ownAttachments(ConvertStorageToAdoc converter, Map<String, LinkResult> own) {
        if (!onlyUsedAttachments) {
            return new ArrayList<>(own.values());
        }
        Set<String> used = converter.usedAttachments();
        List<LinkResult> res = new ArrayList<>();
        for (Map.Entry<String, LinkResult> entry : own.entrySet()) {
            if (used.contains(entry.getKey())) {
                res.add(entry.getValue());
            }
        }
        if (res.size() < own.size()) {
            log.debug("Вложений на странице {}, используется {} — остальные не качаем", own.size(), res.size());
        }
        return res;
    }

    /**
     * Картинки, вставленные с другой страницы ({@code <ri:attachment><ri:page …/></ri:attachment>}): в
     * карте вложений текущей страницы их нет, поэтому в {@code attache/} они не попадали и ссылка в
     * {@code .adoc} вела в никуда. URL берём из rendered view (Confluence уже отрезолвил его там) и
     * докачиваем такие файлы вместе со своими вложениями.
     */
    private List<LinkResult> foreignImages(ConvertStorageToAdoc converter, Map<String, LinkResult> own) {
        Map<String, String> sources = converter.imageSources();
        List<LinkResult> res = new ArrayList<>();
        for (String filename : converter.imageAttachments()) {
            if (own.containsKey(filename)) {
                continue;
            }
            String src = downloadPath(sources.get(filename));
            if (src == null) {
                log.warn("Картинка {} не найдена ни во вложениях страницы, ни в rendered view", filename);
                continue;
            }
            res.add(new LinkResult(filename, src));
        }
        return res;
    }

    /**
     * Приводит ссылку из view к виду, который ждёт {@code loadAttach} — путь относительно хоста
     * Confluence. Чужой хост скачать этим клиентом нельзя, поэтому такие ссылки отбрасываем.
     */
    private String downloadPath(String src) {
        if (src == null) {
            return null;
        }
        String base = client.baseUrl();
        if (!base.isEmpty() && src.startsWith(base)) {
            return src.substring(base.length());
        }
        return src.startsWith("/") ? src : null;
    }

    private static void putIfNotNull(Map<MetadataKey, Object> metadata, MetadataKey key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    /** Ссылки на прямые дочерние страницы (для макроса {@code children}): заголовок + путь в подпапку. */
    private List<LinkResult> childLinks(List<ConfluenceGateway.PageRef> children) {
        return children.stream()
                .filter(c -> c.title() != null)
                .map(c -> new LinkResult(c.title(),
                        sanitizeFolderName(c.title()) + "/" + format.indexFileName()))
                .toList();
    }

    /**
     * Рекурсивно выгружает дочерние страницы в подпапки текущей — параллельно, если включён
     * {@link #exportThreads} > 1 (страницы независимы: у каждой своя папка и свой конвертер).
     */
    private void exportChildren(List<ConfluenceGateway.PageRef> children, Path destination,
                                ProgressCallback progressCallback) {
        ExecutorService executor = exportExecutor;
        if (executor == null || children.size() < 2) {
            for (ConfluenceGateway.PageRef child : children) {
                export(child.id(), destination, progressCallback);
            }
            return;
        }
        List<Future<?>> tasks = new ArrayList<>(children.size());
        for (ConfluenceGateway.PageRef child : children) {
            tasks.add(executor.submit(() -> export(child.id(), destination, progressCallback)));
        }
        awaitAll(tasks);
    }

    /**
     * Дожидается выгрузку всех дочерних страниц и пробрасывает первую ошибку.
     * <p>
     * Ключевое здесь — что метод не возвращает управление, пока жива хоть одна задача. На первой
     * ошибке (в том числе на отмене пользователем) остальные задачи отменяются с прерыванием, но их
     * всё равно дожидаются: иначе выгрузка «завершилась бы» при живых потоках, а вызывающий закрыл бы
     * под ними HTTP-клиент и продолжил писать файлы уже после сообщения об отмене.
     */
    private static void awaitAll(List<Future<?>> tasks) {
        Throwable failure = null;
        for (Future<?> task : tasks) {
            try {
                task.get();
            } catch (CancellationException cancelled) {
                // Задачу отменили мы сами после чужой ошибки — исходную причину уже запомнили.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failure = failure != null ? failure : e;
                cancelPending(tasks);
            } catch (ExecutionException e) {
                if (failure == null) {
                    failure = e.getCause() == null ? e : e.getCause();
                    cancelPending(tasks);
                }
            }
        }
        if (failure != null) {
            throw asUnchecked(failure);
        }
    }

    /** Отменяет ещё не завершённые задачи с прерыванием: продолжать выгрузку после ошибки незачем. */
    private static void cancelPending(List<Future<?>> tasks) {
        for (Future<?> task : tasks) {
            task.cancel(true);
        }
    }

    /** Разворачивает причину так, чтобы наружу ушла исходная ошибка, а не обёртка исполнителя. */
    private static RuntimeException asUnchecked(Throwable cause) {
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(cause);
    }

    /**
     * Пропуск неизменившейся страницы <b>без загрузки её тела</b>: заголовок и версию берём из дерева
     * пред-прохода, поэтому за самой страницей (storage + view + вложения) идти не нужно. Возвращает
     * заголовок пропущенной страницы либо {@code null}, если пропустить нельзя и её надо загружать.
     * <p>
     * Дети всё равно обходятся — каждый проверяется отдельно.
     */
    private PageExport skipUnchangedWithoutLoading(String id, Path baseDir, ProgressCallback progressCallback) {
        if (!skipUnchanged) {
            return null;
        }
        ConfluenceGateway.PageNode node = nodesById.get(id);
        if (node == null || node.version() == null) {
            return null;
        }
        Path destination = baseDir.resolve(sanitizeFolderName(node.title()));
        if (!isUnchanged(destination, node.version())) {
            return null;
        }
        this.destination = destination;
        progressCallback.next("Пропуск (не изменилась): %s".formatted(node.title()), 0.2D);
        return new PageExport(node.title(), destination, includeChildren ? childrenOf(id) : List.of());
    }

    /**
     * Страница не изменилась: в каталоге уже есть {@code index.<ext>} с той же версией ({@code version.when})
     * в заголовке. Нет файла/версии — считаем изменившейся (нужно выгрузить).
     */
    @SneakyThrows
    private boolean isUnchanged(Path destination, String current) {
        if (current == null || current.isBlank()) {
            return false;
        }
        Path index = destination.resolve(format.indexFileName());
        if (Files.notExists(index)) {
            return false;
        }
        Matcher matcher = VERSION_LINE.matcher(Files.readString(index));
        return matcher.find() && current.equals(matcher.group(1).trim());
    }

    /** Удаляет каталог, если он существует и пуст (чтобы не оставлять пустую {@code files/}). */
    private static void deleteIfEmpty(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            if (entries.findAny().isEmpty()) {
                Files.delete(dir);
            }
        }
    }

    /** Делает из заголовка страницы безопасное имя папки. Делегирует в {@link PageFolder#sanitize}. */
    static String sanitizeFolderName(String title) {
        return PageFolder.sanitize(title);
    }

    private void saveLinks(Path source, Map<LinksValue, LinkResult> linksResolvers) throws IOException {
        List<LinkPairSave> list = linksResolvers.entrySet()
                .stream()
                .map(e -> new LinkPairSave(e.getKey(), e.getValue()))
                .toList();
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(source.resolve("links.json").toFile(), list);
    }

    @SneakyThrows
    private Map<LinksValue, LinkResult> loadLinks(Path source) {
        Path linksJsonFile = source.resolve("links.json");
        if (Files.notExists(linksJsonFile)) {
            return Map.of();
        }
        LinkPairSave[] linkPairSaves = objectMapper.readValue(linksJsonFile.toFile(), LinkPairSave[].class);
        return Arrays.stream(linkPairSaves)
                .filter(e -> !Objects.isNull(e.res()))
                .collect(Collectors.toMap(LinkPairSave::key, LinkPairSave::res));
    }

    private Map<LinksValue, LinkResult> getLinks(Map<LinksValue, LinkResult> loadResult,
                                                 Set<LinksValue> links,
                                                 Map<String, String> resolveView,
                                                 Map<String, LinkResult> attachment,
                                                 Path currentFolder,
                                                 Consumer<String> progress) {
        Map<LinksValue, LinkResult> linksResolvers = new HashMap<>(loadResult);
        for (LinksValue link : links) {
            if (linksResolvers.containsKey(link)) {
                log.warn("link ignore load exist {}", link);
                continue;
            }
            // нерезолвленную ссылку в карту не кладём: writer отрисует её плейсхолдером, а не NPE-ит
            switch (link) {
                case LinksUser user -> putIfResolved(linksResolvers, link, resolveUser(user.userKey()));
                case LinksPage page -> putIfResolved(linksResolvers, link,
                        resolveLink(page, resolveView, currentFolder));
                case LinksAttachment pageAttachment -> putIfResolved(linksResolvers, link,
                        attachment.get(pageAttachment.filename()));
                default -> throw new IllegalStateException("Unexpected value: " + link);
            }
            if (progress != null) {
                progress.accept(link.toString());
            }
        }
        return linksResolvers;
    }

    private static void putIfResolved(Map<LinksValue, LinkResult> links, LinksValue key, LinkResult value) {
        if (value != null) {
            links.put(key, value);
        }
    }

    /**
     * Резолв ссылки на страницу: локальный путь внутри выгрузки → URL из rendered view → поиск по
     * заголовку. {@code null}, если страницу найти не удалось (удалена, переименована, нет прав) —
     * тогда ссылка остаётся нерезолвленной, но экспорт не падает.
     */
    private LinkResult resolveLink(LinksPage page, Map<String, String> resolveView, Path currentFolder) {
        LinkResult local = localLink(page.title(), currentFolder);
        if (local != null) {
            return local;
        }
        String url = resolveView.get(page.title());
        if (url != null) {
            return new LinkResult(page.title(), pageUrl(url, currentFolder));
        }
        LinkResult hit = searchPage(page.title(), page.space());
        if (hit == null) {
            return null;
        }
        return new LinkResult(hit.title(), pageUrl(hit.url(), currentFolder));
    }

    /**
     * Поиск страницы по заголовку с кэшем на весь прогон. Кэшируется «сырая» находка, а не готовая
     * ссылка: путь до неё зависит от папки страницы, с которой ссылаются.
     */
    private LinkResult searchPage(String title, String space) {
        return searchCache.computeIfAbsent(title + ' ' + space, key -> {
            List<LinkResult> found = client.search(title, space);
            if (found.isEmpty()) {
                log.warn("Страница «{}» (space={}) не найдена — ссылка остаётся нерезолвленной", title, space);
                return Optional.empty();
            }
            return Optional.of(found.getFirst());
        }).orElse(null);
    }

    /** Резолв пользователя по ключу с кэшем на весь прогон. */
    private LinkResult resolveUser(String userKey) {
        return userCache.computeIfAbsent(userKey, key -> Optional.ofNullable(client.user(key))).orElse(null);
    }

    /**
     * Адрес найденной страницы: локальный путь, если она тоже входит в выгрузку, иначе — абсолютный
     * URL Confluence. По заголовку страница могла не найтись (у ссылки своя подпись), поэтому по URL
     * пробуем ещё раз — там есть {@code pageId}.
     */
    private String pageUrl(String url, Path currentFolder) {
        return localPages(currentFolder).href(url);
    }

    /**
     * Карта страниц выгрузки для страницы в папке {@code currentFolder}. Если локальный резолв выключен
     * (или папка неизвестна — конвертация в память), карта пуста, но базовый URL остаётся: ссылки всё
     * равно нужно достраивать до абсолютных.
     */
    private LocalPages localPages(Path currentFolder) {
        boolean local = crossPageLinks && currentFolder != null;
        return new LocalPages(local ? pageIdFolders : Map.of(), local ? pageFolders : Map.of(),
                currentFolder, format.indexFileName(), client.baseUrl());
    }

    /**
     * Локальная ссылка на страницу поддерева: относительный путь от папки текущей страницы к
     * {@code index.<ext>} целевой. {@code null}, если целевой страницы нет в выгрузке (тогда — обычный
     * резолв в URL Confluence). Якорь ({@code Заголовок#anchor}) переносится в конец пути.
     */
    private LinkResult localLink(String rawTitle, Path currentFolder) {
        if (!crossPageLinks || currentFolder == null) {
            return null;
        }
        int hash = rawTitle.indexOf('#');
        String pageTitle = hash >= 0 ? rawTitle.substring(0, hash) : rawTitle;
        String anchor = hash >= 0 ? rawTitle.substring(hash + 1) : null;
        Path targetFolder = pageFolders.get(pageTitle);
        if (targetFolder == null) {
            return null;
        }
        String rel = localPages(currentFolder).relativeIndex(targetFolder);
        return new LinkResult(pageTitle, anchor == null ? rel : rel + "#" + anchor);
    }

    /** Отдаёт заранее загруженную корневую страницу (из пред-прохода) один раз, иначе грузит по id. */
    private ContentPage takePrefetched(String id) {
        ContentPage page = prefetched.remove(id);
        return page != null ? page : client.getMainPage(id);
    }

    record LinkPairSave(LinksValue key, LinkResult res) {
    }
}
