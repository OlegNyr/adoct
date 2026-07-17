package io.github.adoct.generate.render;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.asciidoctor.ast.Document;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import io.github.adoct.generate.asciidoc.AnchorIndex;
import io.github.adoct.generate.model.RenderResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Моноширинное упоминание файла набора ({@code `file.adoc`} → {@code <code>…</code>}) превращается в ссылку
 * на страницу Confluence, если такой {@code .adoc} реально существует. Заодно проверяем резолвер реального
 * заголовка (по {@code :confluency-id:}): при его наличии он побеждает заголовок из самого файла.
 */
public class CodeMentionLinkRenderTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private RenderResult render(Path main, Function<Path, String> titleResolver) throws Exception {
        return render(main, titleResolver, null);
    }

    private RenderResult render(Path main, Function<Path, String> titleResolver, Path rootDir) throws Exception {
        try (Asciidoctor asciidoctor = Asciidoctor.Factory.create()) {
            String text = Files.readString(main, StandardCharsets.UTF_8);
            Document doc = asciidoctor.load(text, Options.builder()
                    .safe(SafeMode.UNSAFE)
                    .baseDir(main.getParent().toFile())
                    .attributes(Attributes.builder().attribute("outfilesuffix", ".adoc").build())
                    .build());
            return new StorageRenderer("plantuml", main.getParent(), "",
                    AnchorIndex.empty(), main, "DOC", titleResolver, rootDir).render(doc);
        }
    }

    /** Кладёт index.adoc/child.adoc рядом и main.adoc с заданным телом. */
    private Path scaffold(String mainBody) throws Exception {
        Path dir = tmp.getRoot().toPath();
        Files.writeString(dir.resolve("index.adoc"), "= Узел раздела\n\nТекст.\n", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("child.adoc"), "= Дочерняя страница\n\nТекст.\n", StandardCharsets.UTF_8);
        Path main = dir.resolve("main.adoc");
        Files.writeString(main, "= Главная\n\n" + mainBody + "\n", StandardCharsets.UTF_8);
        return main;
    }

    @Test
    public void codeMentionOfExistingAdocBecomesPageLink() throws Exception {
        String xhtml = render(scaffold("Наверх: `index.adoc`."), null).xhtml();
        // текст ссылки — заголовок страницы, а НЕ имя файла index.adoc
        assertContains(xhtml, "<ac:link><ri:page ri:content-title=\"Узел раздела\" ri:space-key=\"DOC\"/>"
                + "<ac:link-body>Узел раздела</ac:link-body></ac:link>");
        assertNotContains(xhtml, "index.adoc");
    }

    @Test
    public void codeMentionOfMissingAdocStaysCode() throws Exception {
        String xhtml = render(scaffold("Пример: `nosuch.adoc`."), null).xhtml();
        assertContains(xhtml, "<code>nosuch.adoc</code>");
        assertNotContains(xhtml, "ri:content-title=\"nosuch");
        assertNotContains(xhtml, "<ac:link>");
    }

    @Test
    public void resolverRealTitleWinsOverFileHeading() throws Exception {
        // Резолвер отдаёт реальный заголовок Confluence, отличный от "= Дочерняя страница" в файле.
        Function<Path, String> resolver = file ->
                file.getFileName().toString().equals("child.adoc") ? "Настоящее имя в Confluence" : null;
        String xhtml = render(scaffold("См. link:child.adoc[тут] и `child.adoc`."), resolver).xhtml();

        // и обычная ссылка, и моноширинное упоминание используют реальный заголовок, а не заголовок файла
        assertContains(xhtml, "ri:content-title=\"Настоящее имя в Confluence\"");
        assertNotContains(xhtml, "ri:content-title=\"Дочерняя страница\"");
        assertContains(xhtml, "<ac:link-body>тут</ac:link-body>");
        // моноширинное `child.adoc` → текст ссылки = реальный заголовок, имени файла нет
        assertContains(xhtml, "<ac:link-body>Настоящее имя в Confluence</ac:link-body>");
        assertNotContains(xhtml, "child.adoc");
    }

    @Test
    public void rootRelativeCodeMentionResolvesFromPublishRoot() throws Exception {
        // Раскладка как у пользователя: файл глубоко, а путь в ссылке — от корня публикации.
        Path root = tmp.getRoot().toPath();
        Files.createDirectories(root.resolve("03-architecture"));
        Files.writeString(root.resolve("03-architecture").resolve("security.adoc"),
                "= Безопасность\n\nТекст.\n", StandardCharsets.UTF_8);
        Path deep = root.resolve("03-architecture").resolve("dbo-draft").resolve("integration");
        Files.createDirectories(deep);
        Path main = deep.resolve("audit.adoc");
        Files.writeString(main,
                "= Аудит\n\n== См. также\n\n* `03-architecture/security.adoc` — требования\n",
                StandardCharsets.UTF_8);

        // без корня — не находит (путь не относительно папки файла) → остаётся кодом
        assertContains(render(main, null, null).xhtml(), "<code>03-architecture/security.adoc</code>");

        // с корнем публикации — резолвится в ссылку на страницу security.adoc
        String withRoot = render(main, null, root).xhtml();
        assertContains(withRoot, "<ri:page ri:content-title=\"Безопасность\" ri:space-key=\"DOC\"/>");
        assertContains(withRoot, "<ac:link-body>Безопасность</ac:link-body>");
        assertNotContains(withRoot, "<code>03-architecture/security.adoc</code>");
    }

    private static void assertContains(String xhtml, String fragment) {
        assertTrue(xhtml, xhtml.contains(fragment));
    }

    private static void assertNotContains(String xhtml, String fragment) {
        assertFalse(xhtml, xhtml.contains(fragment));
    }
}
