package ru.gitverse.adoct;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import ru.gitverse.adoct.client.ConfluenceClient;
import ru.gitverse.adoct.client.ContentPage;
import ru.gitverse.adoct.client.LinkResult;
import ru.gitverse.adoct.post.DubleCaretPostProcesing;
import ru.gitverse.adoct.post.TableCompactPostProcesing;

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

@Slf4j
@RequiredArgsConstructor
public class DispatcherPage {
    private final ConfluenceClient client;
    private final Path basePath;
    private final ObjectMapper objectMapper;
    @Setter
    private boolean exportColors;

    @SneakyThrows
    public String generate(String id, ProgressCallback progressCallback) {

        progressCallback.next("Загрузка основной страницы", 0.2D);
        ContentPage mainPage = client.getMainPage(id);
        Path destination = basePath.resolve(mainPage.title());
        Files.createDirectories(destination);
        ConvertStorageToAdoc converter = new ConvertStorageToAdoc(mainPage.content(), mainPage.view(), destination);

        Path source = destination.resolve("source");
        Files.createDirectories(source);
        Files.writeString(source.resolve("body.storage.html"), mainPage.content());
        Files.writeString(source.resolve("view.storage.html"), mainPage.view());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(source.resolve("content.json").toFile(), mainPage);


        Path attachmentFolder = destination.resolve("attache");
        Files.createDirectories(attachmentFolder);

        Collection<LinkResult> countAttache = mainPage.attachment().values();
        client.loadAttach(countAttache, attachmentFolder,
                filename ->
                        progressCallback.next("Загружаем вложение %s".formatted(filename), 0.3D / countAttache.size()));

        Map<LinksValue, LinkResult> loadLinksResolve = loadLinks(source);
        Map<String, String> resolveView = converter.resolveLink();
        Set<LinksValue> links = converter.getLinks();
        Map<LinksValue, LinkResult> linksResolvers
                = getLinks(loadLinksResolve, links, resolveView, mainPage.attachment(),
                link ->
                        progressCallback.next("Резолвим ссылку %s".formatted(link), 0.2D / links.size())
        );
        saveLinks(source, linksResolvers);

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
        converter.setProcesings(List.of(
                new DubleCaretPostProcesing(),
                new TableCompactPostProcesing()
        ));
        converter.convert(metadata, attachmentFolder);
        return mainPage.title();
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
