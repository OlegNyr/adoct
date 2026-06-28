package ru.gitverse.adoct.parser.golden;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Прочие теги и поведение диспетчера: time, placeholder, fallback неизвестного тега, рекурсия по div.
 */
public class DispatchAndMiscParserTest extends AbstractConvertParserTest {

    @Test
    public void timeEmitsDatetimeAttribute() throws IOException {
        String out = convert("<p><time datetime=\"2024-01-02\"/></p>");
        assertTrue(out.contains("2024-01-02"));
    }

    @Test
    public void placeholderIsDropped() throws IOException {
        String out = convert("<p>видно <ac:placeholder>не видно</ac:placeholder></p>");
        assertTrue(out.contains("видно"));
        assertFalse(out.contains("не видно"));
    }

    @Test
    public void unknownTagFallsBackToText() throws IOException {
        String out = convert("<foobar>содержимое</foobar>");
        assertTrue(out.contains("содержимое"));
    }

    @Test
    public void divRecursesIntoChildren() throws IOException {
        String out = convert("<div><p>внутри дива</p></div>");
        assertTrue(out.contains("внутри дива"));
    }
}
