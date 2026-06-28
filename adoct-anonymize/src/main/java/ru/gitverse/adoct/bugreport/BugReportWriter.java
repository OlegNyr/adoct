package ru.gitverse.adoct.bugreport;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Собирает каталог баг-репорта: {@code report.txt} (см. {@link BugReport#render()}) плюс
 * анонимизированные входные данные в подпапке {@code payload/}, и упаковывает всё в zip рядом.
 *
 * <p>Заполнение {@code payload/} делегируется вызывающему через {@link PayloadFiller} — экспорт
 * использует {@code ExportAnonymizer}, импорт — {@code ImportAnonymizer}. Если анонимизация падает,
 * репорт всё равно создаётся, а ошибка наполнения пишется в {@code payload-error.txt}.
 */
@Slf4j
public final class BugReportWriter {

    /** Заполняет каталог {@code payload} анонимизированными входными данными. */
    @FunctionalInterface
    public interface PayloadFiller {
        void fill(Path payloadDir) throws Exception;
    }

    public record Result(Path reportDir, Path zipFile) {
    }

    @SneakyThrows
    public Result write(BugReport report, Path reportDir, PayloadFiller filler) {
        if (Files.exists(reportDir)) {
            FileUtils.deleteDirectory(reportDir.toFile());
        }
        Files.createDirectories(reportDir);
        Files.writeString(reportDir.resolve("report.txt"), report.render(), StandardCharsets.UTF_8);

        if (filler != null) {
            Path payload = reportDir.resolve("payload");
            Files.createDirectories(payload);
            try {
                filler.fill(payload);
            } catch (Exception e) {
                log.warn("Failed to fill bug report payload", e);
                Files.writeString(reportDir.resolve("payload-error.txt"), trace(e), StandardCharsets.UTF_8);
            }
        }

        Path zipFile = reportDir.resolveSibling(reportDir.getFileName() + ".zip");
        zip(reportDir, zipFile);
        return new Result(reportDir, zipFile);
    }

    private static String trace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
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
