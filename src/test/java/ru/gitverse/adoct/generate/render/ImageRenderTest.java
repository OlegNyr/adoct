package ru.gitverse.adoct.generate.render;

import org.junit.Test;
import ru.gitverse.adoct.generate.model.RenderResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Картинки: эмитят макрос image со ссылкой на вложение и собирают локальный файл для загрузки. */
public class ImageRenderTest extends AbstractStorageRendererTest {

    @Test
    public void imageEmitsAttachmentAndCollectsFile() {
        RenderResult result = render("image::pic.png[Alt]\n", "images");
        assertContains(result.xhtml(), "<ac:image><ri:attachment ri:filename=\"pic.png\"/></ac:image>");
        assertEquals(1, result.images().size());
        String imagePath = result.images().get(0).toString();
        assertTrue(imagePath, imagePath.replace('\\', '/').endsWith("images/pic.png"));
    }
}
