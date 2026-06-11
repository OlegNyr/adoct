package ru.gitverse.adoct.plugins.idea.service;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.extensions.PluginId;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import ru.gitverse.adoct.anonymize.Anonymizer;
import ru.gitverse.adoct.anonymize.ExportAnonymizer;
import ru.gitverse.adoct.anonymize.ImportAnonymizer;
import ru.gitverse.adoct.bugreport.BugReport;
import ru.gitverse.adoct.bugreport.BugReportWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Собирает баг-репорт при неудачной конвертации в любую сторону: рядом с артефактом создаётся
 * каталог {@code <имя>-bugreport-<время>} и одноимённый zip с {@code report.txt} (контекст + стек)
 * и анонимизированными входными данными в {@code payload/}.
 *
 * <p>Движок ({@link ru.gitverse.adoct.bugreport}, {@link ru.gitverse.adoct.anonymize}) не зависит от
 * IntelliJ — этот сервис лишь добавляет версию плагина и обезличивает контекст. Любая ошибка сборки
 * репорта проглатывается (логируется) — она не должна подменять исходную ошибку конвертации.
 */
@Service(Service.Level.APP)
@Slf4j
public final class BugReportService {

    private static final String PLUGIN_ID = "org.AsciiDocTools.plugins.idea";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public static BugReportService getInstance() {
        return ApplicationManager.getApplication().getService(BugReportService.class);
    }

    /**
     * Баг-репорт неудачного экспорта (Confluence → AsciiDoc).
     *
     * @param exportRoot каталог уже выгруженных артефактов ({@code source/}, {@code attache/}) — может
     *                   быть {@code null}, если упали до их сохранения (тогда payload пустой)
     * @return путь к zip-архиву репорта либо {@code null}, если репорт собрать не удалось
     */
    @Nullable
    public Path captureExport(String url, @Nullable Path exportRoot, Throwable error) {
        Map<String, String> context = new LinkedHashMap<>();
        context.put("url", maskUrl(url));
        context.put("export-root", exportRoot == null ? "(not created)" : exportRoot.getFileName().toString());
        return capture("export", context, error, exportRoot, payload -> {
            if (exportRoot != null && Files.isDirectory(exportRoot)) {
                new ExportAnonymizer().anonymizeInto(exportRoot, payload);
            }
        });
    }

    /**
     * Баг-репорт неудачного импорта (AsciiDoc → Confluence).
     *
     * @param source исходный {@code .adoc}-файл или папка, которую публиковали
     * @return путь к zip-архиву репорта либо {@code null}, если репорт собрать не удалось
     */
    @Nullable
    public Path captureImport(String url, Path source, Throwable error) {
        Map<String, String> context = new LinkedHashMap<>();
        context.put("url", maskUrl(url));
        context.put("source", source == null ? "(none)" : source.getFileName().toString());
        return capture("import", context, error, source, payload -> {
            if (source != null && Files.exists(source)) {
                new ImportAnonymizer().anonymizeInto(source, payload);
            }
        });
    }

    @Nullable
    private Path capture(String operation, Map<String, String> context, Throwable error,
                         @Nullable Path artifact, BugReportWriter.PayloadFiller filler) {
        try {
            BugReport report = new BugReport(operation, LocalDateTime.now().toString(),
                    pluginVersion(), context, error);
            Path reportDir = reportDir(operation, artifact);
            return new BugReportWriter().write(report, reportDir, filler).zipFile();
        } catch (Throwable t) {
            log.warn("Failed to build {} bug report", operation, t);
            return null;
        }
    }

    /** Каталог репорта рядом с артефактом (или во временной папке, если артефакта нет). */
    private static Path reportDir(String operation, @Nullable Path artifact) {
        String suffix = "bugreport-" + STAMP.format(LocalDateTime.now());
        if (artifact != null && artifact.getParent() != null) {
            String base = artifact.getFileName() == null ? operation : artifact.getFileName().toString();
            return artifact.resolveSibling(base + "-" + suffix);
        }
        return Path.of(System.getProperty("java.io.tmpdir")).resolve("adoct-" + operation + "-" + suffix);
    }

    /** Обезличивает URL (хост → confluence.example.com, pageId/логины вычищаются) для report.txt. */
    private static String maskUrl(String url) {
        if (url == null || url.isBlank()) {
            return "(none)";
        }
        return new Anonymizer().url(url);
    }

    private static String pluginVersion() {
        try {
            var descriptor = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID));
            return descriptor == null ? "unknown" : descriptor.getVersion();
        } catch (Throwable t) {
            return "unknown";
        }
    }
}
