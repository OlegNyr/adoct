package ru.gitverse.adoct.anonymize;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Оркестратор анонимизации источника импорта (AsciiDoc → Confluence) для приложения к баг-репорту.
 *
 * <p>Берёт одиночный {@code .adoc} или папку с {@code .adoc} и зеркалит структуру в выходной каталог:
 * {@code .adoc} прогоняются через {@link AdocAnonymizer}, прочие файлы (картинки, drawio, вложения) —
 * через {@link AttachmentAnonymizer}, с сохранением имён, чтобы ссылки между файлами продолжали
 * резолвиться при воспроизведении бага. Один {@link Anonymizer} на прогон — подмены согласованы.
 */
@Slf4j
public class ImportAnonymizer {

    private static final String ADOC_SUFFIX = ".adoc";

    public record Result(Path outputDir, int adocFiles, int otherFiles) {
    }

    @SneakyThrows
    public Result anonymizeInto(Path source, Path outputDir) {
        Anonymizer anon = new Anonymizer();
        AdocAnonymizer adoc = new AdocAnonymizer(anon);
        AttachmentAnonymizer attachments = new AttachmentAnonymizer(anon);

        if (Files.exists(outputDir)) {
            FileUtils.deleteDirectory(outputDir.toFile());
        }
        Files.createDirectories(outputDir);

        int[] counts = {0, 0};
        if (Files.isRegularFile(source)) {
            anonymizeFile(source, outputDir.resolve(source.getFileName().toString()), adoc, attachments, counts);
        } else if (Files.isDirectory(source)) {
            try (Stream<Path> walk = Files.walk(source)) {
                for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                    Path target = outputDir.resolve(source.relativize(file).toString());
                    anonymizeFile(file, target, adoc, attachments, counts);
                }
            }
        }
        return new Result(outputDir, counts[0], counts[1]);
    }

    private void anonymizeFile(Path source, Path target, AdocAnonymizer adoc,
                               AttachmentAnonymizer attachments, int[] counts) throws IOException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        if (source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ADOC_SUFFIX)) {
            Files.writeString(target, adoc.anonymize(Files.readString(source, StandardCharsets.UTF_8)),
                    StandardCharsets.UTF_8);
            counts[0]++;
        } else {
            attachments.anonymizeContentTo(source, target);
            counts[1]++;
        }
    }
}
