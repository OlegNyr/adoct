package ru.gitverse.adoct.anonymize;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Оркестратор анонимизации экспорта Confluence для приложения к баг-репорту.
 *
 * <p>Берёт каталог экспорта (или его подпапку {@code source}), анонимизирует
 * {@code body.storage.html} / {@code view.storage.html} / {@code content.json} /
 * {@code links.json} и вложения {@code attache/}, кладёт результат в выходной каталог
 * и упаковывает его в zip. Один {@link Anonymizer} на прогон — подмены согласованы
 * между всеми файлами.
 */
@Slf4j
public class ExportAnonymizer {

    private static final List<String> HTML_FILES = List.of("body.storage.html", "view.storage.html");
    private static final List<String> JSON_FILES = List.of("content.json", "links.json");

    public record Result(Path outputDir, Path zipFile, int htmlFiles, int jsonFiles, int attachments) {
    }

    @SneakyThrows
    public Result anonymize(Path root, Path outputDir, Path zipFile) {
        Result result = anonymizeInto(root, outputDir);
        zip(outputDir, zipFile);
        return new Result(outputDir, zipFile, result.htmlFiles(), result.jsonFiles(), result.attachments());
    }

    /**
     * Анонимизирует экспорт в {@code outputDir} без упаковки в архив (для приложения к баг-репорту,
     * где архив собирается уровнем выше). Поле {@link Result#zipFile()} остаётся {@code null}.
     */
    @SneakyThrows
    public Result anonymizeInto(Path root, Path outputDir) {
        Anonymizer anon = new Anonymizer();
        StorageHtmlAnonymizer html = new StorageHtmlAnonymizer(anon);
        JsonAnonymizer json = new JsonAnonymizer(anon, html);
        AttachmentAnonymizer attachments = new AttachmentAnonymizer(anon);

        if (Files.exists(outputDir)) {
            FileUtils.deleteDirectory(outputDir.toFile());
        }
        Files.createDirectories(outputDir);

        Path htmlDir = locateHtmlDir(root);

        int htmlCount = 0;
        for (String name : HTML_FILES) {
            Path source = htmlDir.resolve(name);
            if (Files.exists(source)) {
                Files.writeString(outputDir.resolve(name), html.anonymizeFragment(Files.readString(source)));
                htmlCount++;
            }
        }

        int jsonCount = 0;
        for (String name : JSON_FILES) {
            Path source = htmlDir.resolve(name);
            if (Files.exists(source)) {
                Files.writeString(outputDir.resolve(name), json.anonymizeJson(Files.readString(source)));
                jsonCount++;
            }
        }

        int attachCount = 0;
        Path attachDir = locateAttachDir(root, htmlDir);
        if (attachDir != null) {
            Path outAttach = outputDir.resolve("attache");
            attachments.process(attachDir, outAttach);
            attachCount = countFiles(outAttach);
        }

        return new Result(outputDir, null, htmlCount, jsonCount, attachCount);
    }

    private static Path locateHtmlDir(Path root) {
        if (Files.exists(root.resolve("body.storage.html"))) {
            return root;
        }
        Path source = root.resolve("source");
        if (Files.exists(source.resolve("body.storage.html"))) {
            return source;
        }
        return root;
    }

    private static Path locateAttachDir(Path root, Path htmlDir) {
        Path[] candidates = {
                root.resolve("attache"),
                htmlDir.resolve("attache"),
                htmlDir.getParent() == null ? null : htmlDir.getParent().resolve("attache"),
                root.getParent() == null ? null : root.getParent().resolve("attache")
        };
        for (Path candidate : candidates) {
            if (candidate != null && Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    @SneakyThrows
    private static int countFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            return (int) files.filter(Files::isRegularFile).count();
        }
    }

    private static void zip(Path sourceDir, Path zipFile) throws IOException {
        if (zipFile.getParent() != null) {
            Files.createDirectories(zipFile.getParent());
        }
        try (OutputStream os = Files.newOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(os, StandardCharsets.UTF_8);
             Stream<Path> files = Files.walk(sourceDir)) {
            files.filter(Files::isRegularFile).forEach(file -> addZipEntry(zos, sourceDir, file));
        }
    }

    @SneakyThrows
    private static void addZipEntry(ZipOutputStream zos, Path root, Path file) {
        String entry = root.relativize(file).toString().replace('\\', '/');
        zos.putNextEntry(new ZipEntry(entry));
        Files.copy(file, zos);
        zos.closeEntry();
    }
}
