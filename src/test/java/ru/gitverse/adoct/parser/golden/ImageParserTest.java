package ru.gitverse.adoct.parser.golden;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertTrue;

/** Изображения Confluence (ac:image): макрос image с размерами. */
public class ImageParserTest extends AbstractConvertParserTest {

    @Test
    public void acImageEmitsImageMacroWithDimensions() throws IOException {
        String out = convert(
                "<ac:image ac:width=\"200\" ac:height=\"100\">"
                + "<ri:attachment ri:filename=\"diagram.png\"/></ac:image>");
        assertTrue(out.contains("image:") || out.contains("image::"));
        assertTrue(out.contains("diagram.png["));
        assertTrue(out.contains("width=200"));
        assertTrue(out.contains("height=100"));
    }
}
