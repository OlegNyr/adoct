package org.tools.asciidoc.plugins.idea.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import lombok.extern.slf4j.Slf4j;
import org.asciidoctor.ast.Document;
import org.jetbrains.annotations.NotNull;
import org.tools.asciidoc.plugins.idea.settings.ConfluenceSettingsService;
import ru.gitverse.adoct.generate.asciidoc.AdocPageTitle;
import ru.gitverse.adoct.generate.asciidoc.AnchorIndex;
import ru.gitverse.adoct.generate.asciidoc.AsciiDocParser;
import ru.gitverse.adoct.generate.confluence.ConfluenceClient;
import ru.gitverse.adoct.generate.confluence.PageVersion;
import ru.gitverse.adoct.generate.model.RenderResult;
import ru.gitverse.adoct.generate.render.StorageRenderer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Публикует AsciiDoc в Confluence (обратная операция к экспорту). Оркестрирует движок
 * {@code ru.gitverse.adoct.generate}: парсинг {@code .adoc} → рендер в storage format → заливка через REST.
 *
 * <p>Источник — одиночный {@code .adoc} ({@code publish}) или папка с {@code .adoc} ({@code publish-dir}).
 * Сервер (host + токен) резолвится через {@link ConfluenceSettingsService} по базовому URL — как в экспорте.
 * Номер страницы необязателен: для файла берётся из URL ({@code pageId=...}) или из {@code :confluency-id:}.
 *
 * <p><b>Иерархия папок.</b> Каждая папка представлена своим {@code index.adoc} (если его нет — создаётся,
 * заголовок = имя папки). {@code index.adoc} папки — страница-узел: обычные файлы папки становятся её
 * детьми, а {@code index.adoc} подпапки — ребёнком {@code index.adoc} родительской папки. Корневой
 * {@code index.adoc} соответствует головной странице из URL ({@code parentId}).
 *
 * <p>Файл с {@code :confluency-id: ignore} пропускается. Картинки и ссылки на существующие локальные
 * файлы загружаются как вложения страницы.
 */
@Service(Service.Level.APP)
@Slf4j
public final class PublishDocsToConfluence {

    private static final String PLANTUML_MACRO = "plantuml";
    private static final String ID_ATTRIBUTE = "confluency-id";
    private static final String IGNORE = "ignore";
    private static final String INDEX_FILE = "index.adoc";
    private static final Pattern PAGE_ID = Pattern.compile("pageId=(\\d+)");

    /** Счётчики итога публикации папки. */
    private static final class Counts {
        private int created;
        private int updated;
        private int skipped;
        private int failed;
    }

    public static PublishDocsToConfluence getInstance() {
        return ApplicationManager.getApplication().getService(PublishDocsToConfluence.class);
    }

    /**
     * Публикует {@code source} (файл или папку) в Confluence по адресу {@code url}.
     *
     * @return краткий итог для уведомления
     */
    public String publish(String url, Path source, @NotNull ProgressIndicator indicator) throws Exception {
        indicator.checkCanceled();
        indicator.setIndeterminate(false);
        indicator.setFraction(0.1);

        ConfluenceSettingsService.ServerEntry server = ConfluenceSettingsService.getInstance()
                .getServer(url)
                .orElseThrow(() -> new RuntimeException(
                        "Server not found in settings for URL: " + url
                                + ". Add it in Settings | Tools | AsciiDocTools Confluence."));
        ConfluenceClient client = new ConfluenceClient(server.getHost(), server.getToken());
        Optional<String> urlPageId = extractPageId(url);

        if (Files.isDirectory(source)) {
            return publishDir(client, source, urlPageId, indicator);
        }
        return publishFile(client, source, urlPageId, indicator);
    }

    private String publishFile(ConfluenceClient client, Path file, Optional<String> urlPageId,
                               ProgressIndicator indicator) throws IOException, InterruptedException {
        indicator.setText2(file.getFileName().toString());
        AnchorIndex index = AnchorIndex.scan(adocFiles(file.getParent(), false));
        try (AsciiDocParser parser = new AsciiDocParser()) {
            Document doc = parser.parse(file);
            if (isIgnored(doc)) {
                indicator.setFraction(1.0);
                return "Skipped " + file.getFileName() + " (:confluency-id: ignore)";
            }
            String pageId = urlPageId.orElseGet(() -> attribute(doc, ID_ATTRIBUTE));
            if (pageId == null || pageId.isBlank()) {
                throw new RuntimeException("No page id: provide a Confluence page URL with pageId, "
                        + "or add :confluency-id: to " + file.getFileName());
            }
            RenderResult result = render(file, doc, index, client.getSpaceKey(pageId));
            PageVersion version = client.getPage(pageId);
            uploadAttachments(client, pageId, result.images());
            // Заголовок страницы сохраняем как есть — обновляем только тело.
            client.updatePage(pageId, version.title(), result.xhtml(), version.number() + 1);
            applyLabels(client, pageId, doc);
            indicator.setFraction(1.0);
            return "Published " + file.getFileName() + " to page " + pageId;
        }
    }

    private String publishDir(ConfluenceClient client, Path dir, Optional<String> parentId,
                              ProgressIndicator indicator) throws IOException, InterruptedException {
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
        resolveRootIndex(client, dir, rootPageId, rootPage.title(), space, index, counts);
        for (Path folder : folderTree(dir, files)) {
            if (folder.equals(dir)) {
                continue;
            }
            indicator.checkCanceled();
            indicator.setText2(folder.getFileName() + "/" + INDEX_FILE);
            String parentPageId = folderPage.getOrDefault(folder.getParent(), rootPageId);
            try {
                folderPage.put(folder, resolveFolderIndex(client, folder, parentPageId, space, index, counts));
            } catch (Exception e) {
                counts.failed++;
                // дети повиснут на ближайшей разрешённой родительской странице
                folderPage.put(folder, parentPageId);
                log.warn("Failed to resolve folder page {}", folder, e);
            }
        }

        // Фаза 2: обычные файлы — дети index-страницы своей папки.
        double step = 0.7 / files.size();
        for (Path file : files) {
            indicator.checkCanceled();
            indicator.setFraction(Math.min(0.95, indicator.getFraction() + step));
            if (isIndex(file)) {
                continue;
            }
            indicator.setText2(file.getFileName().toString());
            String parentPageId = folderPage.getOrDefault(file.getParent(), rootPageId);
            try {
                publishLeaf(client, file, parentPageId, space, index, counts);
            } catch (Exception e) {
                counts.failed++;
                log.warn("Failed to publish {}", file, e);
            }
        }
        indicator.setFraction(1.0);
        return "Folder published: created %d, updated %d, skipped %d, failed %d (of %d files)"
                .formatted(counts.created, counts.updated, counts.skipped, counts.failed, files.size());
    }

    /**
     * Корневой {@code index.adoc} = головная страница ({@code parentId}). Есть файл → обновляем только тело
     * (заголовок родителя сохраняем) и дописываем id; нет файла → создаём трекинг-файл, тело родителя не трогаем.
     */
    private void resolveRootIndex(ConfluenceClient client, Path dir, String parentId, String parentTitle,
                                  String spaceKey, AnchorIndex index, Counts counts)
            throws IOException, InterruptedException {
        Path indexFile = dir.resolve(INDEX_FILE);
        if (!Files.exists(indexFile)) {
            String title = parentTitle == null || parentTitle.isBlank() ? "index" : parentTitle;
            Files.writeString(indexFile, "= " + title + "\n:confluency-id: " + parentId + "\n", StandardCharsets.UTF_8);
            refreshVfs(indexFile);
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
            updateBody(client, parentId, result.xhtml());
            uploadAttachments(client, parentId, result.images());
            applyLabels(client, parentId, doc);
            counts.updated++;
        }
    }

    /** {@code index.adoc} подпапки — страница-узел папки под {@code parentPageId}. Возвращает id этой страницы. */
    private String resolveFolderIndex(ConfluenceClient client, Path folder, String parentPageId, String space,
                                      AnchorIndex index, Counts counts) throws IOException, InterruptedException {
        Path indexFile = folder.resolve(INDEX_FILE);
        if (!Files.exists(indexFile)) {
            // авто-создаём index.adoc (заголовок = имя папки) и публикуем как страницу папки
            Files.writeString(indexFile, "= " + folder.getFileName() + "\n", StandardCharsets.UTF_8);
            refreshVfs(indexFile);
        }
        try (AsciiDocParser parser = new AsciiDocParser()) {
            Document doc = parser.parse(indexFile);
            if (isIgnored(doc)) {
                counts.skipped++;
                return parentPageId;
            }
            RenderResult result = render(indexFile, doc, index, space);
            String existingId = attribute(doc, ID_ATTRIBUTE);
            if (existingId != null && !existingId.isBlank()) {
                updateBody(client, existingId, result.xhtml());
                uploadAttachments(client, existingId, result.images());
                applyLabels(client, existingId, doc);
                counts.updated++;
                return existingId;
            }
            String title = AdocPageTitle.fromFileOrName(indexFile, folder.getFileName().toString());
            String newId = client.createPage(space, parentPageId, title, result.xhtml());
            writeBackConfluencyId(indexFile, newId);
            uploadAttachments(client, newId, result.images());
            applyLabels(client, newId, doc);
            counts.created++;
            return newId;
        }
    }

    /** Обычный (не-{@code index}) файл — дочерняя страница под {@code parentPageId}. */
    private void publishLeaf(ConfluenceClient client, Path file, String parentPageId, String space,
                             AnchorIndex index, Counts counts) throws IOException, InterruptedException {
        try (AsciiDocParser parser = new AsciiDocParser()) {
            Document doc = parser.parse(file);
            if (isIgnored(doc)) {
                counts.skipped++;
                return;
            }
            RenderResult result = render(file, doc, index, space);
            String existingId = attribute(doc, ID_ATTRIBUTE);
            if (existingId != null && !existingId.isBlank()) {
                updateBody(client, existingId, result.xhtml());
                uploadAttachments(client, existingId, result.images());
                applyLabels(client, existingId, doc);
                counts.updated++;
                return;
            }
            String title = AdocPageTitle.fromFileOrName(file, file.getFileName().toString());
            String newId = client.createPage(space, parentPageId, title, result.xhtml());
            writeBackConfluencyId(file, newId);
            uploadAttachments(client, newId, result.images());
            applyLabels(client, newId, doc);
            counts.created++;
        }
    }

    private RenderResult render(Path file, Document doc, AnchorIndex index, String spaceKey) {
        return new StorageRenderer(PLANTUML_MACRO, file.getParent(), attribute(doc, "imagesdir"), index, file, spaceKey)
                .render(doc);
    }

    /** Обновляет только тело страницы, сохраняя текущий заголовок. */
    private void updateBody(ConfluenceClient client, String pageId, String xhtml)
            throws IOException, InterruptedException {
        PageVersion version = client.getPage(pageId);
        client.updatePage(pageId, version.title(), xhtml, version.number() + 1);
    }

    private void uploadAttachments(ConfluenceClient client, String pageId, List<Path> attachments)
            throws IOException, InterruptedException {
        for (Path attachment : attachments) {
            if (Files.isRegularFile(attachment)) {
                client.uploadAttachment(pageId, attachment);
            } else {
                log.warn("Attachment not found, skipped: {}", attachment);
            }
        }
    }

    private static boolean isIndex(Path file) {
        return file.getFileName().toString().equalsIgnoreCase(INDEX_FILE);
    }

    private static boolean isIgnored(Document doc) {
        return IGNORE.equalsIgnoreCase(attribute(doc, ID_ATTRIBUTE));
    }

    /** Проставляет странице метки из атрибута {@code :keywords:} документа (если есть). */
    private void applyLabels(ConfluenceClient client, String pageId, Document doc)
            throws IOException, InterruptedException {
        client.addLabels(pageId, parseKeywords(attribute(doc, "keywords")));
    }

    /** Разбирает атрибут {@code :keywords:} (значения через запятую) в список меток. */
    static List<String> parseKeywords(String keywords) {
        if (keywords == null || keywords.isBlank()) {
            return List.of();
        }
        return Arrays.stream(keywords.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** Все папки от {@code dir} (включительно) до каждого файла, отсортированные сверху вниз (по глубине). */
    static List<Path> folderTree(Path dir, List<Path> files) {
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

    /** Дописывает {@code :confluency-id: <id>} в шапку файла (после строки {@code = Title}) и обновляет VFS. */
    private static void writeBackConfluencyId(Path file, String id) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        Files.writeString(file, insertConfluencyId(content, id), StandardCharsets.UTF_8);
        refreshVfs(file);
    }

    private static void refreshVfs(Path file) {
        VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
        if (vf != null) {
            vf.refresh(false, false);
        }
    }

    /** Вставляет атрибут после первой строки {@code = Title}; если заголовка нет — в начало. */
    static String insertConfluencyId(String content, String id) {
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

    /** Лояльная версия извлечения {@code pageId} из URL: пусто, если параметра нет. */
    static Optional<String> extractPageId(String url) {
        Matcher matcher = PAGE_ID.matcher(url);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
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
