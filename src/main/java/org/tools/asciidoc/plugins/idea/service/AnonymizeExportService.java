package org.tools.asciidoc.plugins.idea.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import lombok.extern.slf4j.Slf4j;
import ru.gitverse.adoct.anonymize.ExportAnonymizer;

import java.nio.file.Path;

/**
 * Прикладной сервис: анонимизация каталога экспорта Confluence для баг-репорта.
 * Тонкая обёртка над {@link ExportAnonymizer} (движок не зависит от IntelliJ).
 */
@Service(Service.Level.APP)
@Slf4j
public final class AnonymizeExportService {

    public static AnonymizeExportService getInstance() {
        return ApplicationManager.getApplication().getService(AnonymizeExportService.class);
    }

    public ExportAnonymizer.Result anonymize(Path root) {
        String name = root.getFileName() == null ? "export" : root.getFileName().toString();
        Path outputDir = root.resolveSibling(name + "-anon");
        Path zipFile = root.resolveSibling(name + "-anon.zip");
        return new ExportAnonymizer().anonymize(root, outputDir, zipFile);
    }
}
