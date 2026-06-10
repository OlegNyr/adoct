package org.tools.asciidoc.plugins.idea.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProgressIndicator;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.tools.asciidoc.plugins.idea.settings.ConfluenceSettingsService;
import ru.gitverse.adoct.DispatcherPage;
import ru.gitverse.adoct.client.ConfluenceClient;
import ru.gitverse.adoct.client.ObjectMapperExt;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service(Service.Level.APP)
@Slf4j
public final class ConvertDocsUrlToAdoc {


    public static ConvertDocsUrlToAdoc getInstance() {
        return ApplicationManager.getApplication().getService(ConvertDocsUrlToAdoc.class);
    }

    public String convert(String url, Path targetDir, boolean exportColors, @NotNull ProgressIndicator indicator) {
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


        String title = dispatcherPage.generate(extractPageId(url), (text, step) -> {
            if (text != null) {
                indicator.setText2(text);
            }
            indicator.setFraction(indicator.getFraction() + step);
            indicator.checkCanceled();
        });


        indicator.checkCanceled();
        indicator.setFraction(1.0);
        return title;
    }

    /**
     * Извлекает идентификатор страницы из URL Confluence.
     *
     * @param url URL страницы Confluence в формате
     *            https://confluence.example.com/pages/viewpage.action?pageId=21497584874
     * @return идентификатор страницы как {@code long}
     * @throws IllegalArgumentException если URL не содержит корректный pageId
     */
    public static String extractPageId(@NotNull String url) {
        Pattern pattern = Pattern.compile("pageId=(\\d+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            try {
                return matcher.group(1);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid page ID in URL: " + url, e);
            }
        }
        throw new IllegalArgumentException("URL does not contain pageId parameter: " + url);
    }

}
