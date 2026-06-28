package ru.gitverse.adoct.plugins.idea.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import ru.gitverse.adoct.plugins.idea.settings.ConfluenceSettingsService;
import ru.gitverse.adoct.parser.DispatcherPage;
import ru.gitverse.adoct.parser.confluence.ConfluenceClient;
import ru.gitverse.adoct.parser.confluence.ObjectMapperExt;

import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service(Service.Level.APP)
@Slf4j
public final class ConvertDocsUrlToAdoc {


    public static ConvertDocsUrlToAdoc getInstance() {
        return ApplicationManager.getApplication().getService(ConvertDocsUrlToAdoc.class);
    }

    public String convert(String url, Path targetDir, boolean exportColors, boolean debug,
                          boolean includeChildren, boolean includeAttachments,
                          @NotNull ProgressIndicator indicator) {
        indicator.checkCanceled();
        indicator.setIndeterminate(false);
        indicator.setFraction(0.1);
        indicator.setText("Preparing export...");

        ConfluenceSettingsService.ServerEntry serverEntry = ConfluenceSettingsService.getInstance()
                .getServer(url)
                .orElseThrow(() -> new RuntimeException("Server not found in setting"));

        ConfluenceClient confluenceClient = new ConfluenceClient(serverEntry.getHost(), serverEntry.getToken());

        DispatcherPage dispatcherPage = new DispatcherPage(confluenceClient,
                targetDir,
                ObjectMapperExt.INSTANT
        );
        dispatcherPage.setExportColors(exportColors);
        dispatcherPage.setDebug(debug);
        dispatcherPage.setIncludeChildren(includeChildren);
        dispatcherPage.setIncludeAttachments(includeAttachments);

        try {
            String title = dispatcherPage.generate(resolvePageId(confluenceClient, url), (text, step) -> {
                if (text != null) {
                    indicator.setText2(text);
                }
                indicator.setFraction(indicator.getFraction() + step);
                indicator.checkCanceled();
            });

            indicator.checkCanceled();
            indicator.setFraction(1.0);
            return title;
        } catch (ProcessCanceledException cancel) {
            throw cancel;
        } catch (Throwable error) {
            Path report = BugReportService.getInstance().captureExport(url, dispatcherPage.getDestination(), error);
            throw withBugReport(error, report);
        }
    }

    /** Дополняет ошибку конвертации путём к собранному баг-репорту (если он создан). */
    private static RuntimeException withBugReport(Throwable error, Path report) {
        String message = error.getMessage();
        if (report != null) {
            message = (message == null ? "" : message + "\n") + "Bug report: " + report;
        }
        return new RuntimeException(message, error);
    }

    /**
     * Резолвит ID страницы из URL Confluence. Если есть {@code pageId=...} — берём его; иначе пробуем
     * «человеческий» URL {@code /display/SPACE/Title} и дорезолвим ID через REST по пространству и заголовку.
     *
     * @throws IllegalArgumentException если ID не извлечь и страница по space+title не найдена
     */
    public static String resolvePageId(@NotNull ConfluenceClient client, @NotNull String url) {
        Optional<String> byId = extractPageId(url);
        if (byId.isPresent()) {
            return byId.get();
        }
        Optional<PublishDocsToConfluence.DisplayRef> ref = PublishDocsToConfluence.extractDisplayRef(url);
        if (ref.isPresent()) {
            return client.findPageId(ref.get().spaceKey(), ref.get().title())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Page not found for space '" + ref.get().spaceKey()
                                    + "' and title '" + ref.get().title() + "': " + url));
        }
        throw new IllegalArgumentException(
                "URL has no pageId and is not a /display/SPACE/Title link: " + url);
    }

    /** Извлекает {@code pageId=...} из URL Confluence; пусто, если параметра нет. */
    public static Optional<String> extractPageId(@NotNull String url) {
        Matcher matcher = Pattern.compile("pageId=(\\d+)").matcher(url);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

}
