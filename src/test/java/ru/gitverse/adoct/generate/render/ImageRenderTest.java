package ru.gitverse.adoct.generate.render;

import org.junit.Test;
import ru.gitverse.adoct.generate.model.RenderResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Блочные картинки: вложение + сбор файла, атрибуты (alt/width/height), обёртка-ссылка. */
public class ImageRenderTest extends AbstractStorageRendererTest {

    @Test
    public void imageEmitsAttachmentAndCollectsFile() {
        RenderResult result = render("image::pic.png[Alt]\n", "images");
        assertContains(result.xhtml(),
                "<ac:image ac:alt=\"Alt\"><ri:attachment ri:filename=\"pic.png\"/></ac:image>");
        assertEquals(1, result.images().size());
        String imagePath = result.images().get(0).toString();
        assertTrue(imagePath, imagePath.replace('\\', '/').endsWith("images/pic.png"));
    }

    @Test
    public void imageWidthAndHeightBecomeAttributes() {
        String xhtml = render("image::pic.png[Схема,200,100]\n").xhtml();
        assertContains(xhtml, "ac:width=\"200\"");
        assertContains(xhtml, "ac:height=\"100\"");
        assertContains(xhtml, "ac:custom-width=\"true\"");
    }

    @Test
    public void linkedImageIsWrappedInAnchor() {
        String xhtml = render("image::pic.png[Лого,link=https://example.com]\n").xhtml();
        assertContains(xhtml, "<a href=\"https://example.com\"><ac:image");
        assertContains(xhtml, "</ac:image></a>");
    }
}
