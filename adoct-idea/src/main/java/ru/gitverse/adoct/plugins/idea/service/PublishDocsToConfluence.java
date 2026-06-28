package ru.gitverse.adoct.plugins.idea.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import ru.gitverse.adoct.generate.AdocPublisher;
import ru.gitverse.adoct.generate.confluence.ConfluenceClient;
import ru.gitverse.adoct.plugins.idea.settings.ConfluenceSettingsService;

import java.nio.file.Path;

/**
 * Плагинная обёртка над библиотечным {@link AdocPublisher}: резолвит сервер (host + токен) из настроек,
 * прокидывает прогресс/отмену в {@link ProgressIndicator}, обновляет VFS после записи {@code :confluency-id:}
 * и собирает баг-репорт при падении. Вся оркестрация публикации — в {@link AdocPublisher} (модуль движка).
 */
@Service(Service.Level.APP)
@Slf4j
public final class PublishDocsToConfluence {

    public static PublishDocsToConfluence getInstance() {
        return ApplicationManager.getApplication().getService(PublishDocsToConfluence.class);
    }

    /**
     * Публикует {@code source} (файл или папку) в Confluence по адресу {@code url}.
     *
     * @return краткий итог для уведомления
     */
    public String publish(String url, Path source, @NotNull ProgressIndicator indicator) throws Exception {
        try {
            indicator.checkCanceled();
            indicator.setIndeterminate(false);
            indicator.setFraction(0.1);

            ConfluenceSettingsService.ServerEntry server = ConfluenceSettingsService.getInstance()
                    .getServer(url)
                    .orElseThrow(() -> new RuntimeException(
                            "Server not found in settings for URL: " + url
                                    + ". Add it in Settings | Tools | AsciiDocTools Confluence."));
            ConfluenceClient client = new ConfluenceClient(server.getHost(), server.getToken());

            String result = new AdocPublisher(client)
                    .progress(message -> {
                        indicator.checkCanceled();
                        indicator.setText2(message);
                    })
                    .onFileWritten(PublishDocsToConfluence::refreshVfs)
                    .publish(url, source);

            indicator.setFraction(1.0);
            return result;
        } catch (ProcessCanceledException cancel) {
            throw cancel;
        } catch (Throwable error) {
            Path report = BugReportService.getInstance().captureImport(url, source, error);
            String message = error.getMessage();
            if (report != null) {
                message = (message == null ? "" : message + "\n") + "Bug report: " + report;
            }
            throw new RuntimeException(message, error);
        }
    }

    private static void refreshVfs(Path file) {
        VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
        if (vf != null) {
            vf.refresh(false, false);
        }
    }
}
