package ru.gitverse.adoct.parser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import ru.gitverse.adoct.parser.confluence.ConfluenceGateway;
import ru.gitverse.adoct.parser.confluence.ContentPage;
import ru.gitverse.adoct.parser.confluence.LinkResult;
import ru.gitverse.adoct.parser.confluence.ObjectMapperExt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Интеграционный тест оркестратора {@link DispatcherPage} с фейковым {@link ConfluenceGateway}
 * (без сети). Покрывает резолв ссылок (page/user/attachment), запись исходников и кэша links.json,
 * а также то, что повторный прогон использует кэш и не дёргает поиск/пользователей заново.
 */
public class DispatcherPageIntegrationTest {

    private static final String CONTENT = String.join("",
            "<h1>Главная</h1>",
            "<p>Вступление.</p>",
            "<p>Ссылка на <ac:link><ri:page ri:content-title=\"Другая страница\" ri:space-key=\"DOCS\"/>",
            "<ac:plain-text-link-body>Другая</ac:plain-text-link-body></ac:link></p>",
            "<p>Автор <ac:link><ri:user ri:userkey=\"u-123\"/></ac:link></p>",
            "<p>Файл <ac:link><ri:attachment ri:filename=\"doc.pdf\"/></ac:link></p>");

    private Path tmp;

    @Before
    public void setUp() throws IOException {
        tmp = Files.createTempDirectory("adoct-dispatcher-test");
    }

    @After
    public void tearDown() throws IOException {
        if (tmp != null && Files.exists(tmp)) {
            try (var walk = Files.walk(tmp)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
    }

    private ContentPage page() {
        return new ContentPage("Главная", "http://confluence/Главная", "2024-01-01T00:00:00.000+0000",
                CONTENT, "<p>view</p>",
                Map.of("doc.pdf", new LinkResult("doc.pdf", "/download/doc.pdf")));
    }

    @Test
    public void resolvesLinksAndWritesSources() throws IOException {
        FakeGateway gateway = new FakeGateway(page());
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);
        dispatcher.setDebug(true); // source/ пишется только в debug

        String title = dispatcher.generate("123", (text, v) -> { });

        assertEquals("Главная", title);
        Path dest = tmp.resolve("Главная");
        String index = Files.readString(dest.resolve("index.adoc")).replace("\r\n", "\n");

        // Заголовок документа
        assertTrue(index.contains("= Главная"));
        // Страница: резолв через search (view не содержит ссылку) -> link на найденный url
        assertTrue(index.contains("link:http://confluence/page/Другая[Другая]"));
        // Пользователь: резолв через user()
        assertTrue(index.contains("link:http://confluence/display/~u-123[Иван]"));
        // Вложение: link на локальную папку attache
        assertTrue(index.contains("link:attache/doc.pdf[doc.pdf]"));

        // Исходники и кэш сохранены (debug)
        assertTrue(Files.exists(dest.resolve("source").resolve("body.storage.html")));
        assertTrue(Files.exists(dest.resolve("source").resolve("content.json")));
        assertTrue(Files.exists(dest.resolve("source").resolve("links.json")));
        // Вложения скачивались
        assertEquals(1, gateway.loadAttachCalls.get());
    }

    @Test
    public void toAdocInlinesLongCodeWithoutWritingFiles() {
        StringBuilder code = new StringBuilder();
        for (int i = 1; i <= 15; i++) {
            code.append("line").append(i).append('\n');
        }
        String content = "<h1>P</h1>"
                + "<ac:structured-macro ac:name=\"code\">"
                + "<ac:parameter ac:name=\"language\">java</ac:parameter>"
                + "<ac:plain-text-body>" + code + "</ac:plain-text-body></ac:structured-macro>";
        ContentPage page = new ContentPage("P", "http://confluence/P", "2024-01-01T00:00:00.000+0000",
                content, "<p>view</p>", Map.of());

        String adoc = new DispatcherPage(new FakeGateway(page), tmp, ObjectMapperExt.INSTANT).toAdoc("1");

        // длинный код инлайнится целиком, без выноса в файл и include::
        assertFalse("не должно быть include:: — " + adoc, adoc.contains("include::"));
        assertTrue(adoc, adoc.contains("line1") && adoc.contains("line15"));
        assertTrue(adoc, adoc.contains("----"));
    }

    @Test
    public void nonDebugRunOmitsSourceAndEmptyFiles() throws IOException {
        DispatcherPage dispatcher = new DispatcherPage(new FakeGateway(page()), tmp, ObjectMapperExt.INSTANT);

        dispatcher.generate("123", (text, v) -> { });

        Path dest = tmp.resolve("Главная");
        assertTrue(Files.exists(dest.resolve("index.adoc")));
        // Без debug папки source/ нет, а пустая files/ удалена
        assertFalse(Files.exists(dest.resolve("source")));
        assertFalse(Files.exists(dest.resolve("files")));
    }

    @Test
    public void exportsChildPagesIntoSubfolders() throws IOException {
        TreeGateway gateway = new TreeGateway();
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);

        dispatcher.generate("root", (text, v) -> { });

        // Корень -> tmp/Корень/, дочерняя -> tmp/Корень/Ребёнок/, внук -> .../Ребёнок/Внук/
        Path root = tmp.resolve("Корень");
        Path child = root.resolve("Ребёнок");
        Path grand = child.resolve("Внук");
        assertTrue(Files.exists(root.resolve("index.adoc")));
        assertTrue(Files.exists(child.resolve("index.adoc")));
        assertTrue(Files.exists(grand.resolve("index.adoc")));
    }

    @Test
    public void secondRunUsesCachedLinks() throws IOException {
        // Первый прогон создаёт links.json (нужен debug, иначе кэш не пишется)
        DispatcherPage first = new DispatcherPage(new FakeGateway(page()), tmp, ObjectMapperExt.INSTANT);
        first.setDebug(true);
        first.generate("123", (text, v) -> { });

        // Второй прогон: поиск/пользователи бросают — успех возможен только за счёт кэша links.json
        FailingResolveGateway cached = new FailingResolveGateway(page());
        DispatcherPage dispatcher = new DispatcherPage(cached, tmp, ObjectMapperExt.INSTANT);
        dispatcher.setDebug(true);

        String title = dispatcher.generate("123", (text, v) -> { });

        assertEquals("Главная", title);
        assertEquals("search() не должен вызываться при кэше", 0, cached.searchCalls.get());
        assertEquals("user() не должен вызываться при кэше", 0, cached.userCalls.get());
        // Ссылки всё ещё на месте
        String index = Files.readString(tmp.resolve("Главная").resolve("index.adoc")).replace("\r\n", "\n");
        assertTrue(index.contains("link:http://confluence/display/~u-123[Иван]"));
    }

    /** Базовый фейк: отдаёт заданную страницу, резолвит ссылки детерминированно, вложения не качает. */
    private static class FakeGateway implements ConfluenceGateway {
        final ContentPage page;
        final AtomicInteger loadAttachCalls = new AtomicInteger();

        FakeGateway(ContentPage page) {
            this.page = page;
        }

        @Override
        public ContentPage getMainPage(String id) {
            return page;
        }

        @Override
        public List<String> getChildPageIds(String id) {
            return List.of();
        }

        @Override
        public List<LinkResult> search(String title, String key) {
            return List.of(new LinkResult(title, "http://confluence/page/" + title));
        }

        @Override
        public LinkResult user(String userKey) {
            return new LinkResult("Иван", "http://confluence/display/~" + userKey);
        }

        @Override
        public void loadAttach(Collection<LinkResult> values, Path attachmentFolder, Consumer<String> progress) {
            loadAttachCalls.incrementAndGet();
        }
    }

    /** Фейк, падающий при попытке резолва ссылок — проверяет, что кэш делает резолв ненужным. */
    private static class FailingResolveGateway extends FakeGateway {
        final AtomicInteger searchCalls = new AtomicInteger();
        final AtomicInteger userCalls = new AtomicInteger();

        FailingResolveGateway(ContentPage page) {
            super(page);
        }

        @Override
        public List<LinkResult> search(String title, String key) {
            searchCalls.incrementAndGet();
            throw new AssertionError("search() вызван, хотя ссылка должна быть в кэше: " + title);
        }

        @Override
        public LinkResult user(String userKey) {
            userCalls.incrementAndGet();
            throw new AssertionError("user() вызван, хотя ссылка должна быть в кэше: " + userKey);
        }
    }

    /** Фейк с деревом из трёх страниц: root → child → grand. Ссылок/вложений нет. */
    private static class TreeGateway implements ConfluenceGateway {
        private final Map<String, ContentPage> pages = Map.of(
                "root", simple("Корень"),
                "child", simple("Ребёнок"),
                "grand", simple("Внук"));
        private final Map<String, List<String>> children = Map.of(
                "root", List.of("child"),
                "child", List.of("grand"),
                "grand", List.of());

        private static ContentPage simple(String title) {
            return new ContentPage(title, "http://confluence/" + title, "2024-01-01T00:00:00.000+0000",
                    "<p>" + title + "</p>", "<p>view</p>", Map.of());
        }

        @Override
        public ContentPage getMainPage(String id) {
            return pages.get(id);
        }

        @Override
        public List<String> getChildPageIds(String id) {
            return children.getOrDefault(id, List.of());
        }

        @Override
        public List<LinkResult> search(String title, String key) {
            return List.of();
        }

        @Override
        public LinkResult user(String userKey) {
            return null;
        }

        @Override
        public void loadAttach(Collection<LinkResult> values, Path attachmentFolder, Consumer<String> progress) {
        }
    }
}
