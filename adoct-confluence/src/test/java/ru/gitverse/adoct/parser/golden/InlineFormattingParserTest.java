package ru.gitverse.adoct.parser.golden;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertTrue;

/** Абзацы и инлайн-форматирование: strong, i, внешние ссылки. */
public class InlineFormattingParserTest extends AbstractConvertParserTest {

    @Test
    public void plainParagraph() throws IOException {
        String out = convert("<p>Просто текст</p>");
        assertTrue(out.contains("Просто текст"));
    }

    @Test
    public void inlineStrongAndItalic() throws IOException {
        String out = convert("<p><strong>жирный</strong> и <i>курсив</i></p>");
        assertTrue(out.contains("**жирный**"));
        assertTrue(out.contains("__курсив__"));
    }

    @Test
    public void inlineExternalLink() throws IOException {
        String out = convert("<p><a href=\"https://example.org\">сайт</a></p>");
        assertTrue(out.contains("link:https://example.org[сайт]"));
    }
}
