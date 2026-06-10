package ru.gitverse.adoct.generate.asciidoc;

import org.asciidoctor.ast.Document;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import ru.gitverse.adoct.generate.model.RenderResult;
import ru.gitverse.adoct.generate.render.StorageRenderer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfluenceIncludeProcessorTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void crossDocXrefBecomesPageLink() throws Exception {
        Path dir = tmp.getRoot().toPath();
        Files.writeString(dir.resolve("other.adoc"),
                "= Другая страница\n\nТекст.\n", StandardCharsets.UTF_8);
        Path main = dir.resolve("main.adoc");
        Files.writeString(main,
                "= Главная\n\nСм. <<other.adoc#sec,подробнее>> и xref:other.adoc[обзор].\n",
                StandardCharsets.UTF_8);

        RenderResult result;
        try (AsciiDocParser parser = new AsciiDocParser()) {
            Document document = parser.parse(main);
            result = new StorageRenderer("plantuml", main.getParent(), "").render(document);
        }
        String xhtml = result.xhtml();
        // ссылка с якорем: заголовок цели берётся из other.adoc
        assertTrue(xhtml, xhtml.contains(
                "<ac:link ac:anchor=\"sec\"><ri:page ri:content-title=\"Другая страница\"/>"
                        + "<ac:link-body>подробнее</ac:link-body></ac:link>"));
        // ссылка без якоря (xref:)
        assertTrue(xhtml, xhtml.contains(
                "<ac:link><ri:page ri:content-title=\"Другая страница\"/>"
                        + "<ac:link-body>обзор</ac:link-body></ac:link>"));
        // не осталось сырых межфайловых <a href="...adoc...">
        assertFalse(xhtml, xhtml.contains("href=\"other.adoc"));
    }

    @Test
    public void sameNameAnchorInOtherFileBecomesPageLink() throws Exception {
        Path dir = tmp.getRoot().toPath();
        // термин csat живёт в отдельном файле как [[csat]]; в главной на него ссылаются <<csat>>
        Files.writeString(dir.resolve("csat.adoc"),
                "= CSAT\n\n[[csat]]CSAT:: Оценка удовлетворённости.\n", StandardCharsets.UTF_8);
        Path main = dir.resolve("main.adoc");
        Files.writeString(main,
                "= Главная\n\nСвязанные термины: <<csat,csat>>.\n", StandardCharsets.UTF_8);

        AnchorIndex index = AnchorIndex.scan(java.util.List.of(main, dir.resolve("csat.adoc")));
        RenderResult result;
        try (AsciiDocParser parser = new AsciiDocParser()) {
            Document document = parser.parse(main);
            result = new StorageRenderer("plantuml", main.getParent(), "", index, main).render(document);
        }
        String xhtml = result.xhtml();
        // ссылка ведёт на страницу «CSAT» (с якорем csat), а не на якорь текущей страницы
        assertTrue(xhtml, xhtml.contains(
                "<ac:link ac:anchor=\"csat\"><ri:page ri:content-title=\"CSAT\"/>"
                        + "<ac:link-body>csat</ac:link-body></ac:link>"));
    }

    @Test
    public void anchorDefinedOnSamePageStaysInPage() throws Exception {
        Path dir = tmp.getRoot().toPath();
        // якорь routing объявлен в этом же файле — ссылка должна остаться внутристраничной
        Path main = dir.resolve("main.adoc");
        Files.writeString(main,
                "= Главная\n\n[[routing]]Маршрутизация:: Текст.\n\n"
                        + "Админ:: Управляет <<routing,маршрутизацией>>.\n", StandardCharsets.UTF_8);

        AnchorIndex index = AnchorIndex.scan(java.util.List.of(main));
        RenderResult result;
        try (AsciiDocParser parser = new AsciiDocParser()) {
            Document document = parser.parse(main);
            result = new StorageRenderer("plantuml", main.getParent(), "", index, main).render(document);
        }
        String xhtml = result.xhtml();
        assertTrue(xhtml, xhtml.contains(
                "<ac:link ac:anchor=\"routing\"><ac:link-body>маршрутизацией</ac:link-body></ac:link>"));
        // не должно появиться ссылки на страницу для якоря своей же страницы
        assertFalse(xhtml, xhtml.contains("ac:anchor=\"routing\"><ri:page"));
    }

    @Test
    public void includeMacroFormat() {
        assertEquals(
                "<ac:structured-macro ac:name=\"include\"><ac:parameter ac:name=\"\">"
                        + "<ac:link><ri:page ri:content-title=\"Моя страница\"/></ac:link>"
                        + "</ac:parameter></ac:structured-macro>",
                ConfluenceIncludeProcessor.includeMacro("Моя страница"));
    }

    @Test
    public void includeBlockHasHeadingBeforeMacro() {
        assertEquals(
                "<h2><ac:link><ri:page ri:content-title=\"Моя страница\"/>"
                        + "<ac:link-body>Моя страница</ac:link-body></ac:link></h2>"
                        + "<ac:structured-macro ac:name=\"include\"><ac:parameter ac:name=\"\">"
                        + "<ac:link><ri:page ri:content-title=\"Моя страница\"/></ac:link>"
                        + "</ac:parameter></ac:structured-macro>",
                ConfluenceIncludeProcessor.includeBlock("Моя страница"));
    }

    @Test
    public void pageTitleFromFirstHeading() throws IOException {
        Path file = tmp.getRoot().toPath().resolve("part.adoc");
        Files.writeString(file, "= Заголовок части\n\nТекст.\n", StandardCharsets.UTF_8);
        assertEquals("Заголовок части", AdocPageTitle.fromFileOrName(file, "part.adoc"));
    }

    @Test
    public void pageTitleStripsInlineFormatting() throws IOException {
        Path file = tmp.getRoot().toPath().resolve("uc.adoc");
        Files.writeString(file, "= UC-MK-12: витрина `nccchat` ↔ фронт `plchat`\n\nТекст.\n",
                StandardCharsets.UTF_8);
        // имя-цель ссылки/включения совпадает с именем страницы (без backticks/тегов)
        assertEquals("UC-MK-12: витрина nccchat ↔ фронт plchat",
                AdocPageTitle.fromFileOrName(file, "uc.adoc"));
    }

    @Test
    public void pageTitleFallsBackToFileName() throws IOException {
        Path file = tmp.getRoot().toPath().resolve("part.adoc");
        Files.writeString(file, "Просто абзац без заголовка.\n", StandardCharsets.UTF_8);
        assertEquals("part", AdocPageTitle.fromFileOrName(file, "part.adoc"));
    }

    @Test
    public void includeBecomesMacroAndIsNotInlined() throws Exception {
        Path main = Path.of(getClass().getResource("/include-main.adoc").toURI());
        RenderResult result;
        try (AsciiDocParser parser = new AsciiDocParser()) {
            Document document = parser.parse(main);
            result = new StorageRenderer("plantuml", main.getParent(), "").render(document);
        }
        String xhtml = result.xhtml();
        assertTrue(xhtml, xhtml.contains(
                "<ri:page ri:content-title=\"Включаемая страница\"/>"));
        assertTrue(xhtml, xhtml.contains("ac:name=\"include\""));
        // перед макросом — заголовок включаемой страницы (ссылка), чтобы вставки не сливались
        assertTrue(xhtml, xhtml.contains(
                "<h2><ac:link><ri:page ri:content-title=\"Включаемая страница\"/>"
                        + "<ac:link-body>Включаемая страница</ac:link-body></ac:link></h2>"));
        // содержимое включаемого файла НЕ должно инлайниться
        assertFalse(xhtml, xhtml.contains("Содержимое включаемой части."));
        // основной текст остаётся
        assertTrue(xhtml, xhtml.contains("Текст до включения."));
        assertTrue(xhtml, xhtml.contains("Текст после включения."));
    }
}
