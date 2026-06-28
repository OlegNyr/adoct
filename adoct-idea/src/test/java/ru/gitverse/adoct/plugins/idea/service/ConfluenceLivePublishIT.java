package ru.gitverse.adoct.plugins.idea.service;

import org.junit.Test;
import ru.gitverse.adoct.generate.confluence.ConfluenceClient;
import ru.gitverse.adoct.generate.confluence.PageVersion;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;

/**
 * Живой smoke-тест публикации против реального Confluence. Прогоняет наш код-путь резолва:
 * {@code /display/SPACE/Title} URL → {@link PublishDocsToConfluence#resolvePageId} → {@link ConfluenceClient}.
 *
 * <p>Активируется ТОЛЬКО при наличии system-property {@code confluence.token}; иначе тест скипается
 * (на CI без стенда). Запуск:
 * <pre>
 * ./gradlew test --tests "*ConfluenceLivePublishIT" \
 *   -Dconfluence.base=http://localhost:8090 \
 *   -Dconfluence.url=http://localhost:8090/display/DDDD/dsfdsaf \
 *   -Dconfluence.token=XXXX
 * </pre>
 */
public class ConfluenceLivePublishIT {

    @Test
    public void resolvesDisplayUrlAndPublishes() throws Exception {
        String token = System.getProperty("confluence.token");
        assumeNotNull("нет confluence.token — стенд недоступен, тест пропущен", token);
        String base = System.getProperty("confluence.base", "http://localhost:8090");
        String url = System.getProperty("confluence.url", base + "/display/DDDD/dsfdsaf");

        ConfluenceClient client = new ConfluenceClient(base, token);

        // 1) наш резолв: из "человеческого" URL без pageId получаем числовой ID
        Optional<String> pageId = PublishDocsToConfluence.resolvePageId(client, url);
        assertTrue("страница из URL не зарезолвилась: " + url, pageId.isPresent());
        System.out.println("resolved pageId=" + pageId.get() + " from " + url);

        // 2) публикуем тело (storage format) с уникальным маркером, поднимая версию
        PageVersion before = client.getPage(pageId.get());
        String marker = "live-publish-" + System.currentTimeMillis();
        String body = "<h2>AsciiDocTools live test</h2>"
                + "<p>Опубликовано из ConfluenceLivePublishIT.</p>"
                + "<p>Маркер: <code>" + marker + "</code></p>";
        client.updatePage(pageId.get(), before.title(), body, before.number() + 1);

        // 3) проверяем, что версия поднялась
        PageVersion after = client.getPage(pageId.get());
        assertEquals(before.number() + 1, after.number());
        System.out.println("published OK -> " + base + "/pages/viewpage.action?pageId=" + pageId.get()
                + " (version " + after.number() + ", marker " + marker + ")");
    }

    /** Экспортный путь: {@code /display/SPACE/Title} → {@link ConvertDocsUrlToAdoc#resolvePageId} → ID. */
    @Test
    public void exportResolvesDisplayUrl() {
        String token = System.getProperty("confluence.token");
        assumeNotNull("нет confluence.token — стенд недоступен, тест пропущен", token);
        String base = System.getProperty("confluence.base", "http://localhost:8090");
        String url = System.getProperty("confluence.url", base + "/display/DDDD/dsfdsaf");

        ru.gitverse.adoct.parser.confluence.ConfluenceClient client =
                new ru.gitverse.adoct.parser.confluence.ConfluenceClient(base, token);
        String pageId = ConvertDocsUrlToAdoc.resolvePageId(client, url);
        assertTrue("ID не зарезолвился для " + url, pageId != null && !pageId.isBlank());
        System.out.println("export resolved pageId=" + pageId + " from " + url);
    }

    /**
     * Полный live-экспорт страницы в папку (Confluence → AsciiDoc). Включается только при
     * {@code -Dconfluence.dest=<папка>}; пишет в {@code <dest>/<title>/}. Прогоняет резолв display-URL
     * и весь {@link ru.gitverse.adoct.parser.DispatcherPage}.
     */
    @Test
    public void exportPageToFolder() throws Exception {
        String token = System.getProperty("confluence.token");
        String dest = System.getProperty("confluence.dest");
        assumeNotNull("нет confluence.token — стенд недоступен", token);
        assumeNotNull("нет confluence.dest — экспорт в папку не запрошен", dest);
        String base = System.getProperty("confluence.base", "http://localhost:8090");
        String url = System.getProperty("confluence.url", base + "/display/DDDD/dddd");

        java.nio.file.Path target = java.nio.file.Path.of(dest);
        java.nio.file.Files.createDirectories(target);

        ru.gitverse.adoct.parser.confluence.ConfluenceClient client =
                new ru.gitverse.adoct.parser.confluence.ConfluenceClient(base, token);
        String pageId = ConvertDocsUrlToAdoc.resolvePageId(client, url);
        System.out.println("export resolved pageId=" + pageId + " from " + url);

        ru.gitverse.adoct.parser.DispatcherPage dispatcher = new ru.gitverse.adoct.parser.DispatcherPage(
                client, target, ru.gitverse.adoct.parser.confluence.ObjectMapperExt.INSTANT);
        String title = dispatcher.generate(pageId, (text, step) -> System.out.println("  ." + text));
        System.out.println("EXPORTED '" + title + "' -> " + dispatcher.getDestination());
    }
}
