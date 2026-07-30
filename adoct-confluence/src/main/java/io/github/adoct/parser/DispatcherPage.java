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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    /** Каталог выгрузки текущей страницы (для баг-репорта при падении). {@code null} до его создания. */
    @Getter
    private Path destination;

    /** Карта поддерева: заголовок страницы → её папка относительно корня выгрузки ({@link #basePath}). */
    private final Map<String, Path> pageFolders = new HashMap<>();
    /** То же поддерево, но по id страницы — ссылки в теле чаще всего идут именно по {@code pageId}. */
    private final Map<String, Path> pageIdFolders = new HashMap<>();
    /** Корневые страницы, загруженные в пред-проходе для карты дерева — переиспользуются в export. */
    private final Map<String, ContentPage> prefetched = new HashMap<>();

    /** Ищет записанную версию в заголовке ({@code :confluency-version:} adoc / {@code confluency-version:} md). */
    private static final Pattern VERSION_LINE = Pattern.compile("(?m)^:?confluency-version:\\s*(.+)$");

    /**
     * Выгружает страницу и всё её поддерево: каждая страница — в свою подпапку
     * {@code <родитель>/<заголовок дочерней>/}. Возвращает заголовок корневой страницы.
     */
    @SneakyThrows
    public String generate(String id, ProgressCallback progressCallback) {
        if (crossPageLinks) {
            buildSiteMap(List.of(id));
        }
        return export(id, basePath, progressCallback);
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
        if (crossPageLinks) {
            buildSiteMap(roots.stream().map(ConfluenceGateway.PageRef::id).toList());
        }
        for (ConfluenceGateway.PageRef root : roots) {
            export(root.id(), basePath, progressCallback);
        }
        return spaceKey;
    }

    /**
     * Пред-проход: строит карту {@code заголовок → папка (относительно корня выгрузки)} для всех
     * поддеревьев (корней), чтобы ссылку на входящую в выгрузку страницу сделать локальной. Корневые
     * страницы загружаем один раз и переиспользуем в {@link #export}. Заголовки потомков берём дёшево
     * из {@code childPages} (без полной загрузки контента).
     */
    private void buildSiteMap(List<String> rootIds) {
        pageFolders.clear();
        pageIdFolders.clear();
        prefetched.clear();
        Set<String> visited = new HashSet<>();
        for (String rootId : rootIds) {
            ContentPage root = client.getMainPage(rootId);
            prefetched.put(rootId, root);
            Path rootFolder = Path.of(sanitizeFolderName(root.title()));
            pageFolders.put(root.title(), rootFolder);
            pageIdFolders.put(rootId, rootFolder);
            walkSiteMap(rootId, rootFolder, visited);
        }
    }

    private void walkSiteMap(String id, Path folder, Set<String> visited) {
        if (!visited.add(id)) {
            return;
        }
        for (ConfluenceGateway.PageRef ref : client.childPages(id)) {
            if (ref.title() == null) {
                continue;
            }
            Path childFolder = folder.resolve(sanitizeFolderName(ref.title()));
            pageFolders.putIfAbsent(ref.title(), childFolder);
            pageIdFolders.putIfAbsent(ref.id(), childFolder);
            walkSiteMap(ref.id(), childFolder, visited);
        }
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

    @SneakyThrows
    private String export(String id, Path baseDir, ProgressCallback progressCallback) {
        progressCallback.next("Загрузка основной страницы", 0.2D);
        ContentPage mainPage = takePrefetched(id);
        Path destination = baseDir.resolve(sanitizeFolderName(mainPage.title()));
        this.destination = destination;
        List<ConfluenceGateway.PageRef> children = includeChildren ? client.childPages(id) : List.of();

        // Инкрементально: не изменившуюся страницу пропускаем, но детей всё равно обходим.
        if (skipUnchanged && isUnchanged(destination, mainPage)) {
            progressCallback.next("Пропуск (не изменилась): %s".formatted(mainPage.title()), 0.2D);
            exportChildren(children, destination, progressCallback);
            return mainPage.title();
        }

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
            List<LinkResult> countAttache = new ArrayList<>(mainPage.attachment().values());
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
        metadata.put(MetadataKey.LABELS, client.labels(id));
        metadata.put(MetadataKey.CHILDREN, childLinks(children));
        if (mainPage.date() != null) {
            metadata.put(MetadataKey.VERSION, mainPage.date());
        }
        converter.convert(metadata, attachmentFolder);

        // files/ и attache/ оставляем, только если в них что-то есть.
        deleteIfEmpty(filesDirectory);
        deleteIfEmpty(attachmentFolder);

        exportChildren(children, destination, progressCallback);
        return mainPage.title();
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

    /** Рекурсивно выгружает дочерние страницы в подпапки текущей. */
    private void exportChildren(List<ConfluenceGateway.PageRef> children, Path destination,
                                ProgressCallback progressCallback) {
        for (ConfluenceGateway.PageRef child : children) {
            export(child.id(), destination, progressCallback);
        }
    }

    /**
     * Страница не изменилась: в каталоге уже есть {@code index.<ext>} с той же версией ({@code version.when})
     * в заголовке. Нет файла/версии — считаем изменившейся (нужно выгрузить).
     */
    @SneakyThrows
    private boolean isUnchanged(Path destination, ContentPage mainPage) {
        String current = mainPage.date();
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
                case LinksUser user -> putIfResolved(linksResolvers, link, client.user(user.userKey()));
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
        List<LinkResult> found = client.search(page.title(), page.space());
        if (found.isEmpty()) {
            log.warn("Страница «{}» (space={}) не найдена — ссылка остаётся нерезолвленной",
                    page.title(), page.space());
            return null;
        }
        LinkResult hit = found.getFirst();
        return new LinkResult(hit.title(), pageUrl(hit.url(), currentFolder));
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
