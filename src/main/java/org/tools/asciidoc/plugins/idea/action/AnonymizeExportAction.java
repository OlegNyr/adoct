package org.tools.asciidoc.plugins.idea.action;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.tools.asciidoc.plugins.idea.service.AnonymizeExportService;
import ru.gitverse.adoct.anonymize.ExportAnonymizer;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Действие «Анонимизировать экспорт для баг-репорта».
 *
 * <p>Доступно в контекстном меню Project View на каталоге. Берёт папку экспорта Confluence
 * (или её подпапку {@code source}), вычищает рабочие/персональные данные, сохраняя структуру
 * для воспроизведения багов парсера, и складывает результат рядом в {@code <имя>-anon} + zip.
 */
public class AnonymizeExportAction extends AnAction {

    private static final String NOTIFICATION_GROUP_ID = "AsciiDocTools.Notifications";

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        e.getPresentation().setEnabledAndVisible(file != null && file.isDirectory());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file == null || !file.isDirectory()) {
            notifyUser(project, "Выберите каталог экспорта", NotificationType.ERROR);
            return;
        }
        Path root = file.toNioPath();

        AtomicReference<ExportAnonymizer.Result> resultRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Анонимизация экспорта", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText("Чистим рабочие и персональные данные...");
                try {
                    resultRef.set(AnonymizeExportService.getInstance().anonymize(root));
                } catch (Throwable ex) {
                    errorRef.set(ex);
                }
            }

            @Override
            public void onSuccess() {
                Throwable error = errorRef.get();
                if (error != null) {
                    notifyUser(project, "Ошибка анонимизации: " + error.getMessage(), NotificationType.ERROR);
                    return;
                }
                ExportAnonymizer.Result result = resultRef.get();
                VirtualFile outDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(result.outputDir());
                if (outDir != null) {
                    outDir.refresh(false, true);
                }
                LocalFileSystem.getInstance().refreshAndFindFileByNioFile(result.zipFile());
                notifyUser(project, String.format(
                        "Готово. HTML: %d, JSON: %d, вложений: %d%nКаталог: %s%nАрхив: %s",
                        result.htmlFiles(), result.jsonFiles(), result.attachments(),
                        result.outputDir(), result.zipFile()), NotificationType.INFORMATION);
            }
        });
    }

    private static void notifyUser(Project project, String content, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification("AsciiDocTools", content, type)
                .notify(project);
    }
}
