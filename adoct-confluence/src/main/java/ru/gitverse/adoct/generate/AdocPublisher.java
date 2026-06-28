package ru.gitverse.adoct.generate;

import lombok.extern.slf4j.Slf4j;
import org.asciidoctor.ast.Document;
import ru.gitverse.adoct.generate.asciidoc.AdocPageTitle;
import ru.gitverse.adoct.generate.asciidoc.AnchorIndex;
import ru.gitverse.adoct.generate.asciidoc.AsciiDocParser;
import ru.gitverse.adoct.generate.confluence.ConfluenceClient;
import ru.gitverse.adoct.generate.confluence.PageVersion;
import ru.gitverse.adoct.generate.model.RenderResult;
import ru.gitverse.adoct.generate.render.StorageRenderer;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Публикует AsciiDoc в Confluence (обратная операция к экспорту). Оркестрирует движок
 * {@code ru.gitverse.adoct.generate}: парсинг {@code .adoc} → рендер в storage format → заливка через REST.
 * Не зависит от IntelliJ: прогресс отдаётся через {@code progress}, а обновление файла (после записи
 * {@code :confluency-id:}) — через {@code onFileWritten} (плагин на нём обновляет VFS).
 *
 * <p>Источник — одиночный {@code .adoc} или папка с {@code .adoc}. Номер страницы необязателен: для файла
 * берётся из URL ({@code pageId=...}), из «человеческого» URL {@code /display/SPACE/Title} (ID дорезолвится
 * по REST) или из {@code :confluency-id:}.
 *
 * <p><b>Иерархия папок.</b> Каждая папка представлена своим {@code index.adoc} (если его нет — создаётся,
 * заголовок = имя папки). {@code index.adoc} папки — страница-узел: обычные файлы папки становятся её
 * детьми, а {@code index.adoc} подпапки — ребёнком {@code index.adoc} родительской папки. Корневой
 * {@code index.adoc} соответствует головной странице из URL ({@code parentId}).
 *
 * <p>Файл с {@code :confluency-id: ignore} пропускается. Картинки и ссылки на существующие локальные
 * файлы загружаются как вложения страницы.
 */
@Slf4j
public final class AdocPublisher {

    private static final String PLANTUML_MACRO = "plantuml";
    private static final String ID_ATTRIBUTE = "confluency-id";
    private static final String IGNORE = "ignore";
    private static final String INDEX_FILE = "index.adoc";
    private static final String CONTENT_HASH_KEY = "content-hash";
    private static final String ATTACHMENT_HASH_SUFFIX = "-attachment-hash";
    private static final Pattern PAGE_ID = Pattern.compile("pageId=(\\d+)");
    /** «Человеческий» URL без номера страницы: {@code /display/SPACE/Title}. */
    private static final Pattern DISPLAY_URL = Pattern.compile("/display/([^/?#]+)/([^/?#]+)");

    private final ConfluenceClient client;
    private Consumer<String> progress = message -> { };
    private Consumer<Path> onFileWritten = file -> { };

    public AdocPublisher(ConfluenceClient client) {
        this.client = client;
    }

    /** Колбэк прогресса (имя текущего файла/узла). По умолчанию — нет. */
    public AdocPublisher progress(Consumer<String> progress) {
        this.progress = progress == null ? message -> { } : progress;
        return this;
    }

    /** Колбэк «файл перезаписан на диске» (для обновления VFS в плагине). По умолчанию — нет. */
    public AdocPublisher onFileWritten(Consumer<Path> onFileWritten) {
        this.onFileWritten = onFileWritten == null ? file -> { } : onFileWritten;
        return this;
    }

    /** Счётчики итога публикации папки. */
    private static final class Counts {
        private int created;
        private int updated;
        private int skipped;
        private int failed;
    }

    /**
     * Публикует {@code source} (файл или папку) в Confluence по адресу {@code url}.
     *
     * @return краткий итог
     */
    public String publish(String url, Path source) throws IOException, InterruptedException {
        Optional<String> urlPageId = resolvePageId(client, url);
        if (Files.isDirectory(source)) {
            return publishDir(source, urlPageId);
        }
        return publishFile(source, urlPageId);
    }

    private String publishFile(Path file, Optional<String> urlPageId) throws IOException, InterruptedException {
        progress.accept(file.getFileName().toString());
        AnchorIndex index = AnchorIndex.scan(adocFiles(file.getParent(), false));
        try (AsciiDocParser parser = new AsciiDocParser()) {
            Document doc = parser.parse(file);
            if (isIgnored(doc)) {
                return "Skipped " + file.getFileName() + " (:confluency-id: ignore)";
            }
            String pageId = urlPageId.isPresent()
                    ? urlPageId.get()
                    : resolveConfluencyId(file, attribute(doc, ID_ATTRIBUTE));
            if (pageId == null || pageId.isBlank()) {
                throw new RuntimeException("No page id: provide a Confluence page URL with pageId, "
                        + "or add :confluency-id: to " + file.getFileName());
            }
            RenderResult result = render(file, doc, index, client.getSpaceKey(pageId));
            boolean changed = updateBody(pageId, result.xhtml());
            uploadAttachments(pageId, result.images());
            applyLabels(pageId, doc);
            return (changed ? "Published " : "Unchanged ") + file.getFileName() + " (page " + pageId + ")";
        }
    }

    private String publishDir(Path dir, Optional<String> parentId) throws IOException, InterruptedException {
        String rootPageId = parentId.orElseThrow(() -> new RuntimeException(
                "For a folder, provide the parent page URL with pageId (the head page new pages are created under)."));
        List<Path> files = adocFiles(dir, true);
        if (files.isEmpty()) {
            throw new RuntimeException("No .adoc files found in " + dir);
        }
        PageVersion rootPage = client.getPage(rootPageId);
        String space = client.getSpaceKey(rootPageId);
        AnchorIndex index = AnchorIndex.scan(files);
        Counts counts = new Counts();

        // Фаза 1: страницы папок по index.adoc, сверху вниз — родитель резолвится раньше детей.
        Map<Path, String> folderPage = new HashMap<>();
        folderPage.put(dir, rootPageId);
        resolveRootIndex(dir, rootPageId, rootPage.title(), space, index, counts);
        for (Path folder : folderTree(dir, files)) {
            if (folder.equals(dir)) {
                continue;
            }
            progress.accept(folder.getFileName() + "/" + INDEX_FILE);
            String parentPageId = folderPage.getOrDefault(folder.getParent(), rootPageId);
            try {
                folderPage.put(folder, resolveFolderIndex(folder, parentPageId, space, index, counts));
            } catch (Exception e) {
                counts.failed++;
                // дети повиснут на ближайшей разрешённой родительской странице
                folderPage.put(folder, parentPageId);
                log.warn("Failed to resolve folder page {}", folder, e);
            }
        }

        // Фаза 2: обычные файлы — дети index-страницы своей папки.
        for (Path file : files) {
            if (isIndex(file)) {
                continue;
            }
            progress.accept(file.getFileName().toString());
            String parentPageId = folderPage.getOrDefault(file.getParent(), rootPageId);
            try {
                publishLeaf(file, parentPageId, space, index, counts);
            } catch (Exception e) {
                counts.failed++;
                log.warn("Failed to publish {}", file, e);
            }
        }
        return "Folder published: created %d, updated %d, skipped %d, failed %d (of %d files)"
                .formatted(counts.created, counts.updated, counts.skipped, counts.failed, files.size());
    }

    /**
     * Корневой {@code index.adoc} = головная страница ({@code parentId}). Есть файл → обновляем только тело
     * (заголовок родителя сохраняем) и дописываем id; нет файла → создаём трекинг-файл, тело родителя не трогаем.
     */
    private void resolveRootIndex(Path dir, String parentId, String parentTitle,
                                  String spaceKey, AnchorIndex index, Counts counts)
            throws IOException, InterruptedException {
        Path indexFile = dir.resolve(INDEX_FILE);
        if (!Files.exists(indexFile)) {
            String title = parentTitle == null || parentTitle.isBlank() ? "index" : parentTitle;
            Files.writeString(indexFile, "= " + title + "\n:confluency-id: " + parentId + "\n", StandardCharsets.UTF_8);
            onFileWritten.accept(indexFile);
            return;
        }
        try (AsciiDocParser parser = new AsciiDocParser()) {
            Document doc = parser.parse(indexFile);
            if (isIgnored(doc)) {
                counts.skipped++;
                return;
            }
            if (attribute(doc, ID_ATTRIBUTE) == null) {
                writeBackConfluencyId(indexFile, parentId);
            }
            RenderResult result = render(indexFile, doc, index, spaceKey);
            boolean changed = updateBody(parentId, result.xhtml());
            uploadAttachments(parentId, result.images());
            applyLabels(parentId, doc);
            if (changed) {
                counts.updated++;
            } else {
                counts.skipped++;
            }
        }
    }

    /** {@code index.adoc} подпапки — страница-узел папки под {@code parentPageId}. Возвращает id этой страницы. */
    private String resolveFolderIndex(Path folder, String parentPageId, String space,
                                      AnchorIndex index, Counts counts) throws IOException, InterruptedException {
        Path indexFile = folder.resolve(INDEX_FILE);
        if (!Files.exists(indexFile)) {
            // авто-создаём index.adoc (заголовок = имя папки) и публикуем как страницу папки
            Files.writeString(indexFile, "= " + folder.getFileName() + "\n", StandardCharsets.UTF_8);
            onFileWritten.accept(indexFile);
        }
        try (AsciiDocParser parser = new AsciiDocParser()) {
            Document doc = parser.parse(indexFile);
            if (isIgnored(doc)) {
                counts.skipped++;
                return parentPageId;
            }
            RenderResult result = render(indexFile, doc, index, space);
            String existingId = resolveConfluencyId(indexFile, attribute(doc, ID_ATTRIBUTE));
            if (existingId != null && !existingId.isBlank()) {
                boolean changed = updateBody(existingId, result.xhtml());
                uploadAttachments(existingId, result.images());
                applyLabels(existingId, doc);
                if (changed) {
                    counts.updated++;
                } else {
                    counts.skipped++;
                }
                return existingId;
            }
            String title = AdocPageTitle.fromFileOrName(indexFile, folder.getFileName().toString());
            String newId = client.createPage(space, parentPageId, title, result.xhtml());
            rememberContentHash(newId, result.xhtml());
            writeBackConfluencyId(indexFile, newId);
            uploadAttachments(newId, result.images());
            applyLabels(newId, doc);
            counts.created++;
            return newId;
        }
    }

    /** Обычный (не-{@code index}) файл — дочерняя страница под {@code parentPageId}. */
    private void publishLeaf(Path file, String parentPageId, String space,
                             AnchorIndex index, Counts counts) throws IOException, InterruptedException {
        try (AsciiDocParser parser = new AsciiDocParser()) {
            Document doc = parser.parse(file);
            if (isIgnored(doc)) {
                counts.skipped++;
                return;
            }
            RenderResult result = render(file, doc, index, space);
            String existingId = resolveConfluencyId(file, attribute(doc, ID_ATTRIBUTE));
            if (existingId != null && !existingId.isBlank()) {
                boolean changed = updateBody(existingId, result.xhtml());
                uploadAttachments(existingId, result.images());
                applyLabels(existingId, doc);
                if (changed) {
                    counts.updated++;
                } else {
                    counts.skipped++;
                }
                return;
            }
            String title = AdocPageTitle.fromFileOrName(file, file.getFileName().toString());
            String newId = client.createPage(space, parentPageId, title, result.xhtml());
            rememberContentHash(newId, result.xhtml());
            writeBackConfluencyId(file, newId);
            uploadAttachments(newId, result.images());
            applyLabels(newId, doc);
            counts.created++;
        }
    }

    private static RenderResult render(Path file, Document doc, AnchorIndex index, String spaceKey) {
        return new StorageRenderer(PLANTUML_MACRO, file.getParent(), attribute(doc, "imagesdir"), index, file, spaceKey)
                .render(doc);
    }

    /**
     * Обновляет тело страницы, сохраняя текущий заголовок, ТОЛЬКО если контент изменился (sha256 тела
     * хранится в content-property {@code content-hash}). Возвращает {@code true}, если обновление было.
     */
    private boolean updateBody(String pageId, String xhtml) throws IOException, InterruptedException {
        String newHash = sha256(xhtml.getBytes(StandardCharsets.UTF_8));
        if (newHash.equals(client.getProperty(pageId, CONTENT_HASH_KEY))) {
            return false;
        }
        PageVersion version = client.getPage(pageId);
        client.updatePage(pageId, version.title(), xhtml, version.number() + 1);
        client.setProperty(pageId, CONTENT_HASH_KEY, newHash);
        return true;
    }

    /** Прописывает хэш тела новой странице (чтобы следующий прогон корректно сравнивал). */
    private void rememberContentHash(String pageId, String xhtml) throws IOException, InterruptedException {
        client.setProperty(pageId, CONTENT_HASH_KEY, sha256(xhtml.getBytes(StandardCharsets.UTF_8)));
    }

    /** Загружает вложения, пропуская неизменённые (sha256 файла хранится в content-property по имени). */
    private void uploadAttachments(String pageId, List<Path> attachments) throws IOException, InterruptedException {
        for (Path attachment : attachments) {
            if (!Files.isRegularFile(attachment)) {
                log.warn("Attachment not found, skipped: {}", attachment);
                continue;
            }
            String key = attachmentHashKey(attachment.getFileName().toString());
            String newHash = sha256(Files.readAllBytes(attachment));
            if (newHash.equals(client.getProperty(pageId, key))) {
                continue;
            }
            client.uploadAttachment(pageId, attachment);
            client.setProperty(pageId, key, newHash);
        }
    }

    private static String attachmentHashKey(String fileName) {
        return sha256(fileName.getBytes(StandardCharsets.UTF_8)) + ATTACHMENT_HASH_SUFFIX;
    }

    public static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static boolean isIndex(Path file) {
        return file.getFileName().toString().equalsIgnoreCase(INDEX_FILE);
    }

    private static boolean isIgnored(Document doc) {
        return IGNORE.equalsIgnoreCase(attribute(doc, ID_ATTRIBUTE));
    }

    /** Проставляет странице метки из атрибута {@code :keywords:} документа (если есть). */
    private void applyLabels(String pageId, Document doc) throws IOException, InterruptedException {
        client.addLabels(pageId, parseKeywords(attribute(doc, "keywords")));
    }

    /** Разбирает атрибут {@code :keywords:} (значения через запятую) в список меток. */
    public static List<String> parseKeywords(String keywords) {
        if (keywords == null || keywords.isBlank()) {
            return List.of();
        }
        return Arrays.stream(keywords.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** Все папки от {@code dir} (включительно) до каждого файла, отсортированные сверху вниз (по глубине). */
    public static List<Path> folderTree(Path dir, List<Path> files) {
        Set<Path> folders = new LinkedHashSet<>();
        folders.add(dir);
        for (Path file : files) {
            Path folder = file.getParent();
            while (folder != null && folder.startsWith(dir)) {
                folders.add(folder);
                if (folder.equals(dir)) {
                    break;
                }
                folder = folder.getParent();
            }
        }
        List<Path> sorted = new ArrayList<>(folders);
        sorted.sort(Comparator.comparingInt(Path::getNameCount));
        return sorted;
    }

    /** Дописывает {@code :confluency-id: <id>} в шапку файла (после строки {@code = Title}). */
    private void writeBackConfluencyId(Path file, String id) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        Files.writeString(file, insertConfluencyId(content, id), StandardCharsets.UTF_8);
        onFileWritten.accept(file);
    }

    /** Перезаписывает значение существующего {@code :confluency-id:} в файле (URL → числовой ID). */
    private void rewriteConfluencyId(Path file, String id) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        Files.writeString(file, replaceConfluencyId(content, id), StandardCharsets.UTF_8);
        onFileWritten.accept(file);
    }

    /** Меняет значение первой строки {@code :confluency-id: ...} на {@code id}; если строки нет — вставляет. */
    public static String replaceConfluencyId(String content, String id) {
        List<String> lines = new ArrayList<>(List.of(content.split("\n", -1)));
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(":confluency-id:")) {
                lines.set(i, ":confluency-id: " + id);
                return String.join("\n", lines);
            }
        }
        return insertConfluencyId(content, id);
    }

    /** Вставляет атрибут после первой строки {@code = Title}; если заголовка нет — в начало. */
    public static String insertConfluencyId(String content, String id) {
        String attribute = ":confluency-id: " + id;
        List<String> lines = new ArrayList<>(List.of(content.split("\n", -1)));
        int titleIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("= ")) {
                titleIndex = i;
                break;
            }
        }
        lines.add(titleIndex + 1, attribute);
        return String.join("\n", lines);
    }

    /**
     * Резолвит номер страницы из URL. Если в URL есть {@code pageId=...} — берём его; иначе пробуем
     * «человеческий» URL {@code /display/SPACE/Title} и дорезолвим ID через REST по пространству и заголовку.
     * Пусто, если ни то ни другое (тогда сработает {@code :confluency-id:} файла).
     */
    public static Optional<String> resolvePageId(ConfluenceClient client, String url)
            throws IOException, InterruptedException {
        Optional<String> byId = extractPageId(url);
        if (byId.isPresent()) {
            return byId;
        }
        Optional<DisplayRef> ref = extractDisplayRef(url);
        if (ref.isEmpty()) {
            return Optional.empty();
        }
        String pageId = client.findPageId(ref.get().spaceKey(), ref.get().title());
        return Optional.ofNullable(pageId);
    }

    /**
     * Резолвит значение атрибута {@code :confluency-id:}. Чистое число — это уже ID. Иначе значение
     * трактуется как URL Confluence и дорезолвится в ID; результат кэшируется обратно в {@code file}
     * числовым ID (если {@code file != null}). Пусто/{@code null} → {@code null}.
     */
    String resolveConfluencyId(Path file, String value) throws IOException, InterruptedException {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (isNumericId(trimmed)) {
            return trimmed;
        }
        String resolved = resolvePageId(client, trimmed).orElse(null);
        if (resolved != null && file != null) {
            rewriteConfluencyId(file, resolved);
        }
        return resolved;
    }

    private static boolean isNumericId(String value) {
        return !value.isEmpty() && value.chars().allMatch(Character::isDigit);
    }

    /** Лояльная версия извлечения {@code pageId} из URL: пусто, если параметра нет. */
    public static Optional<String> extractPageId(String url) {
        Matcher matcher = PAGE_ID.matcher(url);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    /** Ключ пространства и заголовок страницы, разобранные из URL {@code /display/SPACE/Title}. */
    public record DisplayRef(String spaceKey, String title) {
    }

    /**
     * Разбирает «человеческий» URL {@code /display/SPACE/Title}. Заголовок декодируется как в URL
     * (пробелы — {@code +} или {@code %20}). Пусто, если URL не такого вида.
     */
    public static Optional<DisplayRef> extractDisplayRef(String url) {
        Matcher matcher = DISPLAY_URL.matcher(url);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String space = URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
        String title = URLDecoder.decode(matcher.group(2), StandardCharsets.UTF_8);
        if (space.isBlank() || title.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new DisplayRef(space, title));
    }

    private static String attribute(Document doc, String name) {
        Object value = doc.getAttribute(name);
        return value == null ? null : value.toString();
    }

    private static List<Path> adocFiles(Path root, boolean recursive) throws IOException {
        if (root == null || !Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> walk = recursive ? Files.walk(root) : Files.list(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".adoc"))
                    .sorted()
                    .toList();
        }
    }
}
