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

/** Инлайн-картинки (image:x[]): существующий файл → вложение; внешний URL → ri:url; отсутствующий → без изменений. */
public class InlineImageRenderTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private RenderResult render(Path adoc) throws Exception {
        try (AsciiDocParser parser = new AsciiDocParser()) {
            return new StorageRenderer("plantuml", adoc.getParent(), "").render(parser.parse(adoc));
        }
    }

    @Test
    public void existingInlineImageBecomesAttachment() throws Exception {
        Path dir = tmp.getRoot().toPath();
        Files.writeString(dir.resolve("pic.png"), "PNG", StandardCharsets.UTF_8);
        Path main = dir.resolve("main.adoc");
        Files.writeString(main, "= T\n\nВот image:pic.png[схема] в тексте.\n", StandardCharsets.UTF_8);

        RenderResult result = render(main);
        assertTrue(result.xhtml(), result.xhtml().contains(
                "<ac:image ac:inline=\"true\" ac:alt=\"схема\"><ri:attachment ri:filename=\"pic.png\"/></ac:image>"));
        assertTrue(result.images().toString(),
                result.images().stream().anyMatch(p -> p.getFileName().toString().equals("pic.png")));
        assertFalse(result.xhtml(), result.xhtml().contains("<img"));
    }

    @Test
    public void externalInlineImageBecomesUrl() throws Exception {
        Path main = tmp.getRoot().toPath().resolve("main.adoc");
        Files.writeString(main, "= T\n\nЛого image:https://example.com/logo.png[] тут.\n", StandardCharsets.UTF_8);

        RenderResult result = render(main);
        assertTrue(result.xhtml(), result.xhtml().contains(
                "<ri:url ri:value=\"https://example.com/logo.png\"/>"));
        assertTrue(result.images().isEmpty());
        assertFalse(result.xhtml(), result.xhtml().contains("<img"));
    }

    @Test
    public void missingInlineImageStaysImgTag() throws Exception {
        Path main = tmp.getRoot().toPath().resolve("main.adoc");
        Files.writeString(main, "= T\n\nНет image:nope.png[] файла.\n", StandardCharsets.UTF_8);

        RenderResult result = render(main);
        assertFalse(result.xhtml(), result.xhtml().contains("ri:attachment"));
        assertTrue(result.images().isEmpty());
    }
}
