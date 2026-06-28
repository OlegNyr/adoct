package ru.gitverse.adoct.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import ru.gitverse.adoct.parser.confluence.ConfluenceGateway;
import ru.gitverse.adoct.parser.confluence.ContentPage;
import ru.gitverse.adoct.parser.confluence.LinkResult;
import ru.gitverse.adoct.parser.model.LinksAttachment;
import ru.gitverse.adoct.parser.model.LinksPage;
import ru.gitverse.adoct.parser.model.LinksUser;
import ru.gitverse.adoct.parser.model.LinksValue;
import ru.gitverse.adoct.parser.model.MetadataKey;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
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
    /** Каталог выгрузки текущей страницы (для баг-репорта при падении). {@code null} до его создания. */
    @Getter
    private Path destination;

    /**
     * Выгружает страницу и всё её поддерево: каждая страница — в свою подпапку
     * {@code <родитель>/<заголовок дочерней>/}. Возвращает заголовок корневой страницы.
     */
    @SneakyThrows
    public String generate(String id, ProgressCallback progressCallback) {
        return export(id, basePath, progressCallback);
    }

    @SneakyThrows
    private String export(String id, Path baseDir, ProgressCallback progressCallback) {
        progressCallback.next("Загрузка основной страницы", 0.2D);
        ContentPage mainPage = client.getMainPage(id);
        Path destination = baseDir.resolve(sanitizeFolderName(mainPage.title()));
        this.destination = destination;
        Files.createDirectories(destination);
        ConvertStorageToAdoc converter = new ConvertStorageToAdoc(mainPage.content(), mainPage.view(), destination);

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
            Collection<LinkResult> countAttache = mainPage.attachment().values();
            client.loadAttach(countAttache, attachmentFolder,
                    filename ->
                            progressCallback.next("Загружаем вложение %s".formatted(filename),
                                    0.3D / countAttache.size()));
        }

        Map<LinksValue, LinkResult> loadLinksResolve = loadLinks(source);
        Map<String, String> resolveView = converter.resolveLink();
        Set<LinksValue> links = converter.getLinks();
        Map<LinksValue, LinkResult> linksResolvers
                = getLinks(loadLinksResolve, links, resolveView, mainPage.attachment(),
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

        Map<MetadataKey, Object> metadata = Map.ofEntries(
                Map.entry(MetadataKey.LINKS, linksResolvers),
                Map.entry(MetadataKey.TITLE, mainPage.title()),
                Map.entry(MetadataKey.PAGE_ID, id),
                Map.entry(MetadataKey.URL, mainPage.url()),
                Map.entry(MetadataKey.CREATE, mainPage.date()),
                Map.entry(MetadataKey.ATTACH_FOLDER, attachmentFolder),
                Map.entry(MetadataKey.ATTACH_FOLDER_NAME, "attache"),
                Map.entry(MetadataKey.IMAGE, "attache"),
                Map.entry(MetadataKey.DESTINATION_FOLDER, destination),
                Map.entry(MetadataKey.FILES_FOLDER, filesDirectory),
                Map.entry(MetadataKey.FILES_FOLDER_NAME, "files"),
                Map.entry(MetadataKey.COLOR, exportColors)
        );
        converter.convert(metadata, attachmentFolder);

        // files/ и attache/ оставляем, только если в них что-то есть.
        deleteIfEmpty(filesDirectory);
        deleteIfEmpty(attachmentFolder);

        // Рекурсивно выгружаем дочерние страницы в подпапки текущей страницы.
        if (includeChildren) {
            for (String childId : client.getChildPageIds(id)) {
                export(childId, destination, progressCallback);
            }
        }
        return mainPage.title();
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

    /** Делает из заголовка страницы безопасное имя папки (заменяет недопустимые в ФС символы на {@code _}). */
    static String sanitizeFolderName(String title) {
        String trimmed = title == null ? "" : title.strip();
        String safe = trimmed.replaceAll("[<>:\"/\\\\|?*\\x00-\\x1F]", "_").replaceAll("[. ]+$", "");
        return safe.isBlank() ? "page" : safe;
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
                                                 Consumer<String> progress) {
        Map<LinksValue, LinkResult> linksResolvers = new HashMap<>(loadResult);
        for (LinksValue link : links) {
            if (linksResolvers.containsKey(link)) {
                log.warn("link ignore load exist {}", link);
                continue;
            }
            switch (link) {
                case LinksUser user -> linksResolvers.put(link, client.user(user.userKey()));
                case LinksPage page -> linksResolvers.put(link, resolveLink(page, resolveView));
                case LinksAttachment pageAttachment ->
                        linksResolvers.put(link, attachment.get(pageAttachment.filename()));
                default -> throw new IllegalStateException("Unexpected value: " + link);
            }
            if (progress != null) {
                progress.accept(link.toString());
            }
        }
        return linksResolvers;
    }

    private LinkResult resolveLink(LinksPage page, Map<String, String> resolveView) {
        String url = resolveView.get(page.title());
        if (url == null) {
            return client.search(page.title(), page.space()).getFirst();
        } else {
            return new LinkResult(page.title(), url);
        }
    }

    record LinkPairSave(LinksValue key, LinkResult res) {
    }
}
