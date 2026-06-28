package ru.gitverse.adoct.generate.render;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import ru.gitverse.adoct.generate.asciidoc.AsciiDocParser;
import ru.gitverse.adoct.generate.model.RenderResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Ссылки на локальные файлы: существующий файл → вложение Confluence; внешние/несуществующие — без изменений. */
public class FileLinkRenderTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private RenderResult render(Path adoc) throws Exception {
        try (AsciiDocParser parser = new AsciiDocParser()) {
            return new StorageRenderer("plantuml", adoc.getParent(), "").render(parser.parse(adoc));
        }
    }

    @Test
    public void existingLocalFileLinkBecomesAttachment() throws Exception {
        Path dir = tmp.getRoot().toPath();
        Files.writeString(dir.resolve("spec.pdf"), "%PDF-1.4", StandardCharsets.UTF_8);
        Path main = dir.resolve("main.adoc");
        Files.writeString(main, "= T\n\nСм. link:spec.pdf[спецификация].\n", StandardCharsets.UTF_8);

        RenderResult result = render(main);
        assertTrue(result.xhtml(), result.xhtml().contains(
                "<ac:link><ri:attachment ri:filename=\"spec.pdf\"/>"
                        + "<ac:link-body>спецификация</ac:link-body></ac:link>"));
        assertTrue(result.images().toString(),
                result.images().stream().anyMatch(p -> p.getFileName().toString().equals("spec.pdf")));
    }

    @Test
    public void externalLinkStaysPlain() throws Exception {
        Path main = tmp.getRoot().toPath().resolve("main.adoc");
        Files.writeString(main, "= T\n\nСайт https://example.com[пример].\n", StandardCharsets.UTF_8);

        RenderResult result = render(main);
        assertTrue(result.xhtml(), result.xhtml().contains("<a href=\"https://example.com\">пример</a>"));
        assertFalse(result.xhtml(), result.xhtml().contains("ri:attachment"));
        assertTrue(result.images().isEmpty());
    }

    @Test
    public void missingLocalFileStaysPlain() throws Exception {
        Path main = tmp.getRoot().toPath().resolve("main.adoc");
        Files.writeString(main, "= T\n\nНет файла link:nope.pdf[ссылка].\n", StandardCharsets.UTF_8);

        RenderResult result = render(main);
        assertFalse(result.xhtml(), result.xhtml().contains("ri:attachment"));
        assertTrue(result.images().isEmpty());
    }
}
