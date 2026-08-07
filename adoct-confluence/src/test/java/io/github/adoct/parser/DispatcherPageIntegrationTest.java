package io.github.adoct.parser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import io.github.adoct.parser.confluence.ConfluenceGateway;
import io.github.adoct.parser.confluence.ContentPage;
import io.github.adoct.parser.confluence.LinkResult;
import io.github.adoct.parser.confluence.ObjectMapperExt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    public void pageLabelsBecomeKeywordsInAsciiDocAndTagsInMarkdown() throws IOException {
        LabeledGateway gateway = new LabeledGateway(page(), List.of("api", "docs"));

        DispatcherPage adocDispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);
        adocDispatcher.generate("123", (text, v) -> { });
        String adoc = Files.readString(tmp.resolve("Главная").resolve("index.adoc")).replace("\r\n", "\n");
        assertTrue(adoc, adoc.contains(":keywords: api, docs"));

        DispatcherPage mdDispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);
        mdDispatcher.setFormat(OutputFormat.MD);
        mdDispatcher.generate("123", (text, v) -> { });
        String md = Files.readString(tmp.resolve("Главная").resolve("index.md")).replace("\r\n", "\n");
        assertTrue(md, md.contains("tags:") && md.contains("  - api") && md.contains("  - docs"));
    }

    private ContentPage pageWithVersion(String when) {
        return new ContentPage("Главная", "http://confluence/Главная", when, CONTENT, "<p>view</p>",
                Map.of("doc.pdf", new LinkResult("doc.pdf", "/download/doc.pdf")));
    }

    @Test
    public void skipsUnchangedPageOnSecondRun() throws IOException {
        VersionedGateway gateway = new VersionedGateway(pageWithVersion("v1"));
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);

        dispatcher.generate("123", (t, v) -> { });
        Path index = tmp.resolve("Главная").resolve("index.adoc");
        assertTrue(Files.readString(index).contains(":confluency-version: v1"));
        // помечаем файл: если страница пропущена, метка переживёт повторный прогон
        Files.writeString(index, Files.readString(index) + "\nSENTINEL");
        int attachAfterFirst = gateway.loadAttachCalls.get();

        dispatcher.generate("123", (t, v) -> { });

        assertTrue("неизменная страница не должна перевыгружаться", Files.readString(index).contains("SENTINEL"));
        assertEquals("вложения не должны качаться повторно", attachAfterFirst, gateway.loadAttachCalls.get());
    }

    @Test
    public void reExportsWhenVersionChanged() throws IOException {
        VersionedGateway gateway = new VersionedGateway(pageWithVersion("v1"));
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);

        dispatcher.generate("123", (t, v) -> { });
        Path index = tmp.resolve("Главная").resolve("index.adoc");
        Files.writeString(index, Files.readString(index) + "\nSENTINEL");

        gateway.setPage(pageWithVersion("v2"));
        dispatcher.generate("123", (t, v) -> { });

        String out = Files.readString(index);
        assertFalse("изменившаяся страница должна перевыгрузиться (метка стёрта)", out.contains("SENTINEL"));
        assertTrue(out.contains(":confluency-version: v2"));
    }

    @Test
    public void reExportsWhenSkipUnchangedDisabled() throws IOException {
        VersionedGateway gateway = new VersionedGateway(pageWithVersion("v1"));
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);
        dispatcher.setSkipUnchanged(false);

        dispatcher.generate("123", (t, v) -> { });
        Path index = tmp.resolve("Главная").resolve("index.adoc");
        Files.writeString(index, Files.readString(index) + "\nSENTINEL");

        dispatcher.generate("123", (t, v) -> { });

        assertFalse("при выключенном skip страница выгружается всегда", Files.readString(index).contains("SENTINEL"));
    }

    @Test
    public void crossPageLinksBecomeLocalRelativePaths() throws IOException {
        CrossLinkGateway gateway = new CrossLinkGateway();
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);

        dispatcher.generate("p", (t, v) -> { });

        String parent = Files.readString(tmp.resolve("Родитель").resolve("index.adoc")).replace("\r\n", "\n");
        String child = Files.readString(tmp.resolve("Родитель").resolve("Ребёнок").resolve("index.adoc"))
                .replace("\r\n", "\n");
        // родитель → ребёнок: локальная ссылка в подпапку
        assertTrue(parent, parent.contains("link:Ребёнок/index.adoc[Ребёнок]"));
        // ребёнок → родитель: относительная ссылка вверх
        assertTrue(child, child.contains("link:../index.adoc[Родитель]"));
        // ссылка на страницу вне выгрузки остаётся URL Confluence
        assertTrue(parent, parent.contains("link:http://confluence/page/Внешняя[Внешняя]"));
    }

    @Test
    public void crossPageLinksLocalInMarkdown() throws IOException {
        CrossLinkGateway gateway = new CrossLinkGateway();
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);
        dispatcher.setFormat(OutputFormat.MD);

        dispatcher.generate("p", (t, v) -> { });

        String parent = Files.readString(tmp.resolve("Родитель").resolve("index.md")).replace("\r\n", "\n");
        assertTrue(parent, parent.contains("[Ребёнок](Ребёнок/index.md)"));
    }

    @Test
    public void crossPageLinksStayUrlsWhenDisabled() throws IOException {
        CrossLinkGateway gateway = new CrossLinkGateway();
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);
        dispatcher.setCrossPageLinks(false);

        dispatcher.generate("p", (t, v) -> { });

        String parent = Files.readString(tmp.resolve("Родитель").resolve("index.adoc")).replace("\r\n", "\n");
        assertFalse(parent, parent.contains("Ребёнок/index.adoc"));
        assertTrue(parent, parent.contains("link:http://confluence/page/Ребёнок[Ребёнок]"));
    }

    @Test
    public void childrenMacroBecomesListOfLocalLinks() throws IOException {
        ChildrenGateway gateway = new ChildrenGateway();
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);

        dispatcher.generate("p", (t, v) -> { });

        String parent = Files.readString(tmp.resolve("Родитель").resolve("index.adoc")).replace("\r\n", "\n");
        // пробел в имени папки кодируется — иначе AsciiDoctor не распознает цель ссылки
        assertTrue(parent, parent.contains("* link:Раздел%20A/index.adoc[Раздел A]"));
        assertTrue(parent, parent.contains("* link:Раздел%20B/index.adoc[Раздел B]"));
    }

    @Test
    public void childrenMacroLocalLinksInMarkdown() throws IOException {
        ChildrenGateway gateway = new ChildrenGateway();
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);
        dispatcher.setFormat(OutputFormat.MD);

        dispatcher.generate("p", (t, v) -> { });

        String parent = Files.readString(tmp.resolve("Родитель").resolve("index.md")).replace("\r\n", "\n");
        assertTrue(parent, parent.contains("- [Раздел A](Раздел%20A/index.md)"));
        assertTrue(parent, parent.contains("- [Раздел B](Раздел%20B/index.md)"));
    }

    @Test
    public void exportsWholeSpaceWithRootPagesAndCrossLinks() throws IOException {
        SpaceGateway gateway = new SpaceGateway();
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);

        String space = dispatcher.generateSpace("DOC", (t, v) -> { });

        assertEquals("DOC", space);
        // обе корневые страницы + поддерево первой
        assertTrue(Files.exists(tmp.resolve("Гайд").resolve("index.adoc")));
        assertTrue(Files.exists(tmp.resolve("API").resolve("index.adoc")));
        assertTrue(Files.exists(tmp.resolve("Гайд").resolve("Установка").resolve("index.adoc")));
        // перекрёстная ссылка между корнями резолвится локально
        String guide = Files.readString(tmp.resolve("Гайд").resolve("index.adoc")).replace("\r\n", "\n");
        assertTrue(guide, guide.contains("link:../API/index.adoc[API]"));
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
        dispatcher.setSkipUnchanged(false); // проверяем кэш ссылок, а не пропуск неизменной страницы

        String title = dispatcher.generate("123", (text, v) -> { });

        assertEquals("Главная", title);
        assertEquals("search() не должен вызываться при кэше", 0, cached.searchCalls.get());
        assertEquals("user() не должен вызываться при кэше", 0, cached.userCalls.get());
        // Ссылки всё ещё на месте
        String index = Files.readString(tmp.resolve("Главная").resolve("index.adoc")).replace("\r\n", "\n");
        assertTrue(index.contains("link:http://confluence/display/~u-123[Иван]"));
    }

    @Test
    public void missingPageDoesNotFailExport() throws IOException {
        NotFoundGateway gateway = new NotFoundGateway(page());
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);

        String title = dispatcher.generate("123", (text, v) -> { });

        assertEquals("Главная", title);
        // Ненайденная страница не роняет экспорт, остальные ссылки на месте
        String index = Files.readString(tmp.resolve("Главная").resolve("index.adoc")).replace("\r\n", "\n");
        assertTrue(index, index.contains("= Главная"));
        assertTrue(index, index.contains("link:attache/doc.pdf[doc.pdf]"));
    }

    @Test
    public void relativeViewLinkBecomesAbsolute() throws IOException {
        // view отдаёт ссылку относительной от хоста — в .adoc она должна попасть абсолютной
        ContentPage page = new ContentPage("Главная", "http://confluence/Главная", "2024-01-01T00:00:00.000+0000",
                CONTENT, "<p><a href=\"/pages/viewpage.action?pageId=42\">Другая</a></p>", Map.of());
        DispatcherPage dispatcher = new DispatcherPage(new HostedGateway(page), tmp, ObjectMapperExt.INSTANT);

        dispatcher.generate("123", (text, v) -> { });

        String index = Files.readString(tmp.resolve("Главная").resolve("index.adoc")).replace("\r\n", "\n");
        assertTrue(index, index.contains("link:http://confluence/pages/viewpage.action?pageId=42[Другая]"));
    }

    @Test
    public void imageFromAnotherPageIsDownloaded() {
        String content = "<h1>Главная</h1><p><ac:image><ri:attachment ri:filename=\"чужая.png\">"
                + "<ri:page ri:content-title=\"Картинки\"/></ri:attachment></ac:image></p>";
        String view = "<p><img src=\"/download/thumbnails/999/%D1%87%D1%83%D0%B6%D0%B0%D1%8F.png\""
                + " data-image-src=\"/download/attachments/999/%D1%87%D1%83%D0%B6%D0%B0%D1%8F.png?version=1\""
                + " data-linked-resource-default-alias=\"чужая.png\"/></p>";
        ContentPage page = new ContentPage("Главная", "http://confluence/Главная", "2024-01-01T00:00:00.000+0000",
                content, view, Map.of());
        RecordingGateway gateway = new RecordingGateway(page);

        new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT).generate("123", (text, v) -> { });

        assertEquals(List.of("чужая.png"), gateway.loaded.stream().map(LinkResult::title).toList());
        assertEquals("/download/attachments/999/%D1%87%D1%83%D0%B6%D0%B0%D1%8F.png?version=1",
                gateway.loaded.getFirst().url());
    }

    @Test
    public void onlyUsedAttachmentsSkipsUnreferencedFiles() {
        RecordingGateway gateway = new RecordingGateway(pageWithAttachments());
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);
        dispatcher.setOnlyUsedAttachments(true);

        dispatcher.generate("123", (t, v) -> { });

        // doc.pdf на странице есть (ac:link), старый черновик — нет
        assertEquals(List.of("doc.pdf"), gateway.loaded.stream().map(LinkResult::title).sorted().toList());
    }

    @Test
    public void allAttachmentsDownloadedWhenFilterDisabled() {
        RecordingGateway gateway = new RecordingGateway(pageWithAttachments());
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);

        dispatcher.generate("123", (t, v) -> { });

        assertEquals(List.of("doc.pdf", "черновик-2019.docx"),
                gateway.loaded.stream().map(LinkResult::title).sorted().toList());
    }

    /** Страница с двумя вложениями, из которых тело ссылается только на {@code doc.pdf}. */
    private ContentPage pageWithAttachments() {
        return new ContentPage("Главная", "http://confluence/Главная", "2024-01-01T00:00:00.000+0000",
                CONTENT, "<p>view</p>",
                Map.of("doc.pdf", new LinkResult("doc.pdf", "/download/doc.pdf"),
                        "черновик-2019.docx", new LinkResult("черновик-2019.docx", "/download/черновик.docx")));
    }

    @Test
    public void confluencePageUrlsBecomeLocalPaths() throws IOException {
        UrlLinkGateway gateway = new UrlLinkGateway();
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);

        dispatcher.generate("p", (t, v) -> { });

        String parent = Files.readString(tmp.resolve("Родитель").resolve("index.adoc")).replace("\r\n", "\n");
        // «сырой» <a href> по pageId → путь к файлу дочерней страницы
        assertTrue(parent, parent.contains("link:Ребёнок/index.adoc[по id]"));
        // «человеческий» /display/SPACE/Title → тот же путь, якорь сохраняется
        assertTrue(parent, parent.contains("link:Ребёнок/index.adoc#раздел[по заголовку]"));
        // страница вне выгрузки остаётся ссылкой на Confluence — но абсолютной
        assertTrue(parent, parent.contains("link:http://confluence/pages/viewpage.action?pageId=999[чужая]"));
    }

    @Test
    public void viewResolvedLinkWithOwnCaptionBecomesLocalPath() throws IOException {
        // у ac:link своя подпись, поэтому по заголовку страница не находится — выручает pageId из view
        UrlLinkGateway gateway = new UrlLinkGateway();
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);

        dispatcher.generate("p", (t, v) -> { });

        String parent = Files.readString(tmp.resolve("Родитель").resolve("index.adoc")).replace("\r\n", "\n");
        assertTrue(parent, parent.contains("link:Ребёнок/index.adoc[Подробнее]"));
    }

    @Test
    public void spacesInLinkTargetAreEncoded() throws IOException {
        // link:путь с пробелом[…] AsciiDoctor вообще не считает ссылкой — цель обрывается на пробеле
        CrossLinkGateway gateway = new SpacedTitleGateway();
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);

        dispatcher.generate("p", (t, v) -> { });

        String parent = Files.readString(tmp.resolve("Родитель").resolve("index.adoc")).replace("\r\n", "\n");
        assertTrue(parent, parent.contains("link:Раздел%20про%20всё/index.adoc[Раздел про всё]"));
    }

    @Test
    public void spaceTreeReplacesPerPageChildRequests() throws IOException {
        // Выборка по пространству — прямая, ей можно доверять как списку детей
        SpaceBulkTreeGateway gateway = new SpaceBulkTreeGateway();
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);

        dispatcher.generateSpace("DOC", (t, v) -> { });

        assertEquals("childPages() не должен вызываться при пакетном дереве пространства",
                0, gateway.childPagesCalls.get());
        assertTrue(Files.exists(tmp.resolve("Родитель").resolve("index.adoc")));
        assertTrue(Files.exists(tmp.resolve("Родитель").resolve("Ребёнок").resolve("index.adoc")));
    }

    @Test
    public void subtreeStillAsksForChildrenPerPage() throws IOException {
        // subtree() — это CQL поверх поискового индекса: он отстаёт и режет глубокую пагинацию,
        // поэтому список детей берём запросом, а пакетную выдачу — только для карты и версий
        BulkTreeGateway gateway = new BulkTreeGateway();
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);

        dispatcher.generate("p", (t, v) -> { });

        assertTrue("дети поддерева должны запрашиваться напрямую", gateway.childPagesCalls.get() > 0);
        assertTrue(Files.exists(tmp.resolve("Родитель").resolve("Ребёнок").resolve("Внук").resolve("index.adoc")));
    }

    @Test
    public void bulkTreeBuildsFoldersAndLocalLinks() throws IOException {
        BulkTreeGateway gateway = new BulkTreeGateway();
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);

        dispatcher.generate("p", (t, v) -> { });

        Path root = tmp.resolve("Родитель");
        assertTrue(Files.exists(root.resolve("index.adoc")));
        assertTrue(Files.exists(root.resolve("Ребёнок").resolve("index.adoc")));
        assertTrue(Files.exists(root.resolve("Ребёнок").resolve("Внук").resolve("index.adoc")));
        // Карта папок из пакетной выдачи так же делает перекрёстную ссылку локальной
        String parent = Files.readString(root.resolve("index.adoc")).replace("\r\n", "\n");
        assertTrue(parent, parent.contains("link:Ребёнок/Внук/index.adoc[Внук]"));
    }

    @Test
    public void fallsBackToWalkingWhenBulkTreeEmpty() throws IOException {
        // Гейтвей не умеет spaceTree/subtree — должен отработать обход childPages, как раньше
        CrossLinkGateway gateway = new CrossLinkGateway();
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);

        dispatcher.generate("p", (t, v) -> { });

        String parent = Files.readString(tmp.resolve("Родитель").resolve("index.adoc")).replace("\r\n", "\n");
        assertTrue(parent, parent.contains("link:Ребёнок/index.adoc[Ребёнок]"));
    }

    @Test
    public void unchangedPageIsSkippedWithoutLoadingItsBody() throws IOException {
        BulkTreeGateway gateway = new BulkTreeGateway();
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);

        dispatcher.generate("p", (t, v) -> { });
        Path child = tmp.resolve("Родитель").resolve("Ребёнок").resolve("index.adoc");
        Files.writeString(child, Files.readString(child) + "\nSENTINEL");
        int childLoadsAfterFirst = gateway.mainPageCalls.getOrDefault("c", 0);

        dispatcher.generate("p", (t, v) -> { });

        // Версия известна из дерева — за телом неизменившейся страницы идти незачем
        assertEquals("тело неизменившейся страницы не должно загружаться",
                childLoadsAfterFirst, (int) gateway.mainPageCalls.getOrDefault("c", 0));
        assertTrue("страница не должна перевыгружаться", Files.readString(child).contains("SENTINEL"));
    }

    @Test
    public void changedPageIsReExportedEvenWithKnownVersion() throws IOException {
        BulkTreeGateway gateway = new BulkTreeGateway();
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);

        dispatcher.generate("p", (t, v) -> { });
        Path child = tmp.resolve("Родитель").resolve("Ребёнок").resolve("index.adoc");
        Files.writeString(child, Files.readString(child) + "\nSENTINEL");

        gateway.version = "v2";
        dispatcher.generate("p", (t, v) -> { });

        assertFalse("изменившаяся страница должна перевыгрузиться", Files.readString(child).contains("SENTINEL"));
    }

    @Test
    public void parallelExportWritesWholeTree() throws IOException {
        WideTreeGateway gateway = new WideTreeGateway(12);
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);

        dispatcher.generate("root", (t, v) -> { });

        assertWideTreeExported(12);
    }

    @Test
    public void sequentialExportWritesSameTree() throws IOException {
        WideTreeGateway gateway = new WideTreeGateway(12);
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);
        dispatcher.setExportThreads(1);

        dispatcher.generate("root", (t, v) -> { });

        assertWideTreeExported(12);
    }

    private void assertWideTreeExported(int width) throws IOException {
        Path root = tmp.resolve("Корень");
        assertTrue(Files.exists(root.resolve("index.adoc")));
        for (int i = 0; i < width; i++) {
            Path child = root.resolve("Страница " + i);
            assertTrue("нет страницы " + i, Files.exists(child.resolve("index.adoc")));
            assertTrue("нет вложенной страницы " + i,
                    Files.exists(child.resolve("Вложенная " + i).resolve("index.adoc")));
            assertTrue(Files.readString(child.resolve("index.adoc")).contains("Страница " + i));
        }
    }

    /** Широкое дерево: корень → N страниц, у каждой по одной вложенной. Дерево отдаётся пакетом. */
    private static class WideTreeGateway extends FakeGateway {
        private final int width;

        WideTreeGateway(int width) {
            super(null);
            this.width = width;
        }

        @Override
        public List<ConfluenceGateway.PageNode> subtree(String rootId) {
            List<ConfluenceGateway.PageNode> nodes = new ArrayList<>();
            for (int i = 0; i < width; i++) {
                nodes.add(new ConfluenceGateway.PageNode("c" + i, "Страница " + i, "root", null));
                nodes.add(new ConfluenceGateway.PageNode("g" + i, "Вложенная " + i, "c" + i, null));
            }
            return nodes;
        }

        @Override
        public ContentPage getMainPage(String id) {
            String title = titleOf(id);
            return new ContentPage(title, "http://confluence/" + title, "2024-01-01T00:00:00.000+0000",
                    "<h1>" + title + "</h1><p>тело " + title + "</p>", "<p>view</p>", Map.of());
        }

        @Override
        public List<ConfluenceGateway.PageRef> childPages(String id) {
            List<ConfluenceGateway.PageRef> refs = new ArrayList<>();
            if ("root".equals(id)) {
                for (int i = 0; i < width; i++) {
                    refs.add(new ConfluenceGateway.PageRef("c" + i, "Страница " + i));
                }
            } else if (id.startsWith("c")) {
                String index = id.substring(1);
                refs.add(new ConfluenceGateway.PageRef("g" + index, "Вложенная " + index));
            }
            return refs;
        }

        private String titleOf(String id) {
            if ("root".equals(id)) {
                return "Корень";
            }
            String index = id.substring(1);
            return id.startsWith("c") ? "Страница " + index : "Вложенная " + index;
        }
    }

    @Test
    public void attachmentDownloadFailureAbortsTheExport() {
        // Молчаливый пропуск давал бы .adoc со ссылкой на несуществующий файл и «успешный» экспорт
        ContentPage page = new ContentPage("Главная", "http://confluence/Главная", "v1", CONTENT, "<p>view</p>",
                Map.of("doc.pdf", new LinkResult("doc.pdf", "/download/doc.pdf")));
        DispatcherPage dispatcher = new DispatcherPage(new BrokenAttachmentGateway(page), tmp,
                ObjectMapperExt.INSTANT);

        try {
            dispatcher.generate("123", (t, v) -> { });
            org.junit.Assert.fail("сбой скачивания вложения должен ронять выгрузку");
        } catch (RuntimeException expected) {
            assertTrue(expected.toString(), rootCauseMessage(expected).contains("502"));
        }
    }

    private static String rootCauseMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return String.valueOf(cause.getMessage());
    }

    /** Фейк, у которого скачивание вложения падает как при 5xx от Confluence. */
    private static class BrokenAttachmentGateway extends FakeGateway {
        BrokenAttachmentGateway(ContentPage page) {
            super(page);
        }

        @Override
        public void loadAttach(Collection<LinkResult> values, Path attachmentFolder, Consumer<String> progress) {
            super.loadAttach(values, attachmentFolder, progress);
            throw new IllegalStateException("Confluence ответил 502 на " + values.iterator().next().title());
        }
    }

    @Test
    public void unreachableNodeDoesNotBecomeLocalLink() throws IOException {
        // Родителя «Сироты» нет в выдаче (закрыт правами) — страница не выгружается, значит и
        // локальной ссылки на неё быть не должно, иначе ссылка вела бы в несуществующий файл
        OrphanTreeGateway gateway = new OrphanTreeGateway();
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);

        dispatcher.generate("p", (t, v) -> { });

        assertFalse(Files.exists(tmp.resolve("Сирота")));
        String parent = Files.readString(tmp.resolve("Родитель").resolve("index.adoc")).replace("\r\n", "\n");
        assertTrue(parent, parent.contains("link:http://confluence/page/Сирота[Сирота]"));
        // достижимый ребёнок по-прежнему резолвится локально
        assertTrue(parent, parent.contains("link:Ребёнок/index.adoc[Ребёнок]"));
    }

    @Test
    public void bugReportPointsAtThePageThatFailed() {
        FailingPageGateway gateway = new FailingPageGateway("Страница 1");
        DispatcherPage dispatcher = new DispatcherPage(gateway, tmp, ObjectMapperExt.INSTANT);

        try {
            dispatcher.generate("root", (t, v) -> { });
            org.junit.Assert.fail("выгрузка должна была упасть");
        } catch (RuntimeException expected) {
            // ожидаемо
        }

        // При параллельной выгрузке «текущей» к этому моменту была бы произвольная страница
        assertEquals("Страница 1", dispatcher.getDestination().getFileName().toString());
    }

    /** Дерево, где у одной страницы родитель отсутствует в выдаче — она недостижима от корня. */
    private static class OrphanTreeGateway extends FakeGateway {
        private final Map<String, String> titles = Map.of("p", "Родитель", "c", "Ребёнок", "o", "Сирота");

        OrphanTreeGateway() {
            super(null);
        }

        @Override
        public List<ConfluenceGateway.PageNode> subtree(String rootId) {
            return List.of(new ConfluenceGateway.PageNode("c", "Ребёнок", "p", null),
                    new ConfluenceGateway.PageNode("o", "Сирота", "недоступный-родитель", null));
        }

        @Override
        public ContentPage getMainPage(String id) {
            String title = titles.get(id);
            String content = "p".equals(id)
                    ? "<h1>Родитель</h1>" + linkTo("Ребёнок") + linkTo("Сирота")
                    : "<h1>" + title + "</h1><p>тело</p>";
            return new ContentPage(title, "http://confluence/" + title, "2024-01-01T00:00:00.000+0000",
                    content, "<p>view</p>", Map.of());
        }

        @Override
        public List<ConfluenceGateway.PageRef> childPages(String id) {
            return "p".equals(id) ? List.of(new ConfluenceGateway.PageRef("c", "Ребёнок")) : List.of();
        }

        private static String linkTo(String title) {
            return "<p><ac:link><ri:page ri:content-title=\"" + title + "\"/>"
                    + "<ac:plain-text-link-body>" + title + "</ac:plain-text-link-body></ac:link></p>";
        }
    }

    /** Дерево из трёх страниц, где выгрузка одной из них падает на скачивании вложений. */
    private static class FailingPageGateway extends FakeGateway {
        private final String failOn;

        FailingPageGateway(String failOn) {
            super(null);
            this.failOn = failOn;
        }

        @Override
        public List<ConfluenceGateway.PageNode> subtree(String rootId) {
            List<ConfluenceGateway.PageNode> nodes = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                nodes.add(new ConfluenceGateway.PageNode("c" + i, "Страница " + i, "root", null));
            }
            return nodes;
        }

        @Override
        public ContentPage getMainPage(String id) {
            String title = "root".equals(id) ? "Корень" : "Страница " + id.substring(1);
            return new ContentPage(title, "http://confluence/" + title, "2024-01-01T00:00:00.000+0000",
                    "<h1>" + title + "</h1><p>тело</p>", "<p>view</p>",
                    Map.of("f.pdf", new LinkResult("f.pdf", "/download/f.pdf")));
        }

        @Override
        public List<ConfluenceGateway.PageRef> childPages(String id) {
            if (!"root".equals(id)) {
                return List.of();
            }
            List<ConfluenceGateway.PageRef> refs = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                refs.add(new ConfluenceGateway.PageRef("c" + i, "Страница " + i));
            }
            return refs;
        }

        @Override
        public void loadAttach(Collection<LinkResult> values, Path attachmentFolder, Consumer<String> progress) {
            if (attachmentFolder.getParent().getFileName().toString().equals(failOn)) {
                throw new IllegalStateException("не удалось скачать вложение страницы " + failOn);
            }
        }
    }

    /**
     * Фейк, умеющий отдать всё поддерево одним пакетом ({@code subtree}) и падающий на попытке
     * обойти дерево по одной странице.
     */
    private static class BulkTreeGateway extends FakeGateway {
        final AtomicInteger childPagesCalls = new AtomicInteger();
        final Map<String, Integer> mainPageCalls = new java.util.HashMap<>();
        String version = "v1";
        private final Map<String, String> titles = Map.of("p", "Родитель", "c", "Ребёнок", "g", "Внук");

        BulkTreeGateway() {
            super(null);
        }

        @Override
        public List<ConfluenceGateway.PageNode> subtree(String rootId) {
            return List.of(new ConfluenceGateway.PageNode("c", "Ребёнок", "p", version),
                    new ConfluenceGateway.PageNode("g", "Внук", "c", version));
        }

        @Override
        public ContentPage getMainPage(String id) {
            mainPageCalls.merge(id, 1, Integer::sum);
            String title = titles.get(id);
            String content = "p".equals(id)
                    ? "<h1>Родитель</h1><p>См. <ac:link><ri:page ri:content-title=\"Внук\"/>"
                            + "<ac:plain-text-link-body>Внук</ac:plain-text-link-body></ac:link></p>"
                    : "<h1>" + title + "</h1><p>тело</p>";
            // Версия страницы совпадает с версией в дереве — иначе быстрый пропуск не сработал бы
            return new ContentPage(title, "http://confluence/" + title, version,
                    content, "<p>view</p>", Map.of());
        }

        @Override
        public List<ConfluenceGateway.PageRef> childPages(String id) {
            childPagesCalls.incrementAndGet();
            return switch (id) {
                case "p" -> List.of(new ConfluenceGateway.PageRef("c", "Ребёнок"));
                case "c" -> List.of(new ConfluenceGateway.PageRef("g", "Внук"));
                default -> List.of();
            };
        }
    }

    /**
     * То же дерево, но отданное как выборка по пространству: ей доверяем как полному списку детей,
     * поэтому {@code childPages} не должен вызываться вовсе.
     */
    private static class SpaceBulkTreeGateway extends BulkTreeGateway {
        @Override
        public List<ConfluenceGateway.PageRef> spaceRootPages(String spaceKey) {
            return List.of(new ConfluenceGateway.PageRef("p", "Родитель"));
        }

        @Override
        public List<ConfluenceGateway.PageNode> spaceTree(String spaceKey) {
            return List.of(new ConfluenceGateway.PageNode("p", "Родитель", null, version),
                    new ConfluenceGateway.PageNode("c", "Ребёнок", "p", version));
        }

        @Override
        public List<ConfluenceGateway.PageRef> childPages(String id) {
            childPagesCalls.incrementAndGet();
            throw new AssertionError("childPages() вызван, хотя дерево пространства получено пакетом: " + id);
        }
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

    /**
     * Фейк с деревом «Родитель → Ребёнок», где страницы ссылаются друг на друга (и на внешнюю страницу),
     * с заголовками потомков в {@code childPages} — для проверки локальных перекрёстных ссылок.
     */
    private static class CrossLinkGateway extends FakeGateway {
        private final Map<String, ConfluenceGateway.PageRef> refs = Map.of(
                "p", new ConfluenceGateway.PageRef("p", "Родитель"),
                "c", new ConfluenceGateway.PageRef("c", "Ребёнок"));

        CrossLinkGateway() {
            super(null);
        }

        private static String linkTo(String title) {
            return "<p>См. <ac:link><ri:page ri:content-title=\"" + title + "\" ri:space-key=\"DOC\"/>"
                    + "<ac:plain-text-link-body>" + title + "</ac:plain-text-link-body></ac:link></p>";
        }

        @Override
        public ContentPage getMainPage(String id) {
            ConfluenceGateway.PageRef ref = refs.get(id);
            String content = "p".equals(id)
                    ? "<h1>" + ref.title() + "</h1>" + linkTo("Ребёнок") + linkTo("Внешняя")
                    : "<h1>" + ref.title() + "</h1>" + linkTo("Родитель");
            return new ContentPage(ref.title(), "http://confluence/" + ref.title(),
                    "2024-01-01T00:00:00.000+0000", content, "<p>view</p>", Map.of());
        }

        @Override
        public List<ConfluenceGateway.PageRef> childPages(String id) {
            return "p".equals(id) ? List.of(refs.get("c")) : List.of();
        }

        @Override
        public List<String> getChildPageIds(String id) {
            return childPages(id).stream().map(ConfluenceGateway.PageRef::id).toList();
        }
    }

    /** Фейк пространства «DOC»: два корня (Гайд→Установка, API), Гайд ссылается на API. */
    private static class SpaceGateway extends FakeGateway {
        private final Map<String, String> titles = Map.of(
                "r1", "Гайд", "c1", "Установка", "r2", "API");

        SpaceGateway() {
            super(null);
        }

        @Override
        public List<ConfluenceGateway.PageRef> spaceRootPages(String spaceKey) {
            return List.of(new ConfluenceGateway.PageRef("r1", "Гайд"),
                    new ConfluenceGateway.PageRef("r2", "API"));
        }

        @Override
        public ContentPage getMainPage(String id) {
            String title = titles.get(id);
            String link = "<p>См. <ac:link><ri:page ri:content-title=\"API\" ri:space-key=\"DOC\"/>"
                    + "<ac:plain-text-link-body>API</ac:plain-text-link-body></ac:link></p>";
            String content = "r1".equals(id) ? "<h1>Гайд</h1>" + link : "<h1>" + title + "</h1><p>тело</p>";
            return new ContentPage(title, "http://confluence/" + title, "2024-01-01T00:00:00.000+0000",
                    content, "<p>view</p>", Map.of());
        }

        @Override
        public List<ConfluenceGateway.PageRef> childPages(String id) {
            return "r1".equals(id) ? List.of(new ConfluenceGateway.PageRef("c1", "Установка")) : List.of();
        }

        @Override
        public List<String> getChildPageIds(String id) {
            return childPages(id).stream().map(ConfluenceGateway.PageRef::id).toList();
        }
    }

    /** Фейк «Родитель + два раздела» с макросом {@code children} в теле родителя. */
    private static class ChildrenGateway extends FakeGateway {
        private final Map<String, String> titles = Map.of("p", "Родитель", "a", "Раздел A", "b", "Раздел B");

        ChildrenGateway() {
            super(null);
        }

        @Override
        public ContentPage getMainPage(String id) {
            String title = titles.get(id);
            String content = "p".equals(id)
                    ? "<h1>Родитель</h1><ac:structured-macro ac:name=\"children\"/>"
                    : "<h1>" + title + "</h1><p>тело</p>";
            return new ContentPage(title, "http://confluence/" + title, "2024-01-01T00:00:00.000+0000",
                    content, "<p>view</p>", Map.of());
        }

        @Override
        public List<ConfluenceGateway.PageRef> childPages(String id) {
            return "p".equals(id)
                    ? List.of(new ConfluenceGateway.PageRef("a", "Раздел A"),
                            new ConfluenceGateway.PageRef("b", "Раздел B"))
                    : List.of();
        }

        @Override
        public List<String> getChildPageIds(String id) {
            return childPages(id).stream().map(ConfluenceGateway.PageRef::id).toList();
        }
    }

    /** Фейк с меняемой версией страницы — для проверки инкрементальной выгрузки. */
    private static class VersionedGateway extends FakeGateway {
        private ContentPage current;

        VersionedGateway(ContentPage page) {
            super(page);
            this.current = page;
        }

        void setPage(ContentPage page) {
            this.current = page;
        }

        @Override
        public ContentPage getMainPage(String id) {
            return current;
        }
    }

    /** Фейк с метками страницы — для проверки экспорта labels в {@code :keywords:}/{@code tags}. */
    private static class LabeledGateway extends FakeGateway {
        private final List<String> labels;

        LabeledGateway(ContentPage page, List<String> labels) {
            super(page);
            this.labels = labels;
        }

        @Override
        public List<String> labels(String id) {
            return labels;
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

    /**
     * Фейк «Родитель → Ребёнок», где родитель ссылается на ребёнка URL'ами Confluence (по {@code pageId},
     * по {@code /display/SPACE/Title}) и {@code ac:link}'ом со своей подписью, а также на чужую страницу.
     */
    private static class UrlLinkGateway extends FakeGateway {
        UrlLinkGateway() {
            super(null);
        }

        @Override
        public String baseUrl() {
            return "http://confluence";
        }

        @Override
        public ContentPage getMainPage(String id) {
            if (!"p".equals(id)) {
                return new ContentPage("Ребёнок", "http://confluence/Ребёнок", "2024-01-01T00:00:00.000+0000",
                        "<h1>Ребёнок</h1><p>тело</p>", "<p>view</p>", Map.of());
            }
            String content = "<h1>Родитель</h1>"
                    + "<p><a href=\"http://confluence/pages/viewpage.action?pageId=c\">по id</a></p>"
                    + "<p><a href=\"/display/DOC/%D0%A0%D0%B5%D0%B1%D1%91%D0%BD%D0%BE%D0%BA#раздел\">"
                    + "по заголовку</a></p>"
                    + "<p><a href=\"/pages/viewpage.action?pageId=999\">чужая</a></p>"
                    + "<p><ac:link><ri:page ri:content-title=\"Ребёнок\"/>"
                    + "<ac:plain-text-link-body>Подробнее</ac:plain-text-link-body></ac:link></p>";
            String view = "<p><a href=\"/pages/viewpage.action?pageId=c\">Подробнее</a></p>";
            return new ContentPage("Родитель", "http://confluence/Родитель", "2024-01-01T00:00:00.000+0000",
                    content, view, Map.of());
        }

        @Override
        public List<ConfluenceGateway.PageRef> childPages(String id) {
            return "p".equals(id) ? List.of(new ConfluenceGateway.PageRef("c", "Ребёнок")) : List.of();
        }

        @Override
        public List<String> getChildPageIds(String id) {
            return childPages(id).stream().map(ConfluenceGateway.PageRef::id).toList();
        }
    }

    /** Дерево «Родитель → Раздел про всё»: в заголовке дочерней страницы пробелы. */
    private static class SpacedTitleGateway extends CrossLinkGateway {
        @Override
        public ContentPage getMainPage(String id) {
            String content = "p".equals(id)
                    ? "<h1>Родитель</h1><p><ac:link><ri:page ri:content-title=\"Раздел про всё\"/></ac:link></p>"
                    : "<h1>Раздел про всё</h1><p>тело</p>";
            String title = "p".equals(id) ? "Родитель" : "Раздел про всё";
            return new ContentPage(title, "http://confluence/" + title, "2024-01-01T00:00:00.000+0000",
                    content, "<p>view</p>", Map.of());
        }

        @Override
        public List<ConfluenceGateway.PageRef> childPages(String id) {
            return "p".equals(id) ? List.of(new ConfluenceGateway.PageRef("c", "Раздел про всё")) : List.of();
        }
    }

    /** Фейк, у которого поиск ничего не находит — страница удалена/переименована/нет прав. */
    private static class NotFoundGateway extends FakeGateway {
        NotFoundGateway(ContentPage page) {
            super(page);
        }

        @Override
        public List<LinkResult> search(String title, String key) {
            return List.of();
        }
    }

    /** Фейк с известным базовым URL — для достройки относительных ссылок из view. */
    private static class HostedGateway extends FakeGateway {
        HostedGateway(ContentPage page) {
            super(page);
        }

        @Override
        public String baseUrl() {
            return "http://confluence";
        }
    }

    /** Фейк, запоминающий, какие вложения запросили на скачивание. */
    private static class RecordingGateway extends FakeGateway {
        final List<LinkResult> loaded = new ArrayList<>();

        RecordingGateway(ContentPage page) {
            super(page);
        }

        @Override
        public void loadAttach(Collection<LinkResult> values, Path attachmentFolder, Consumer<String> progress) {
            super.loadAttach(values, attachmentFolder, progress);
            loaded.addAll(values);
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
