package ru.gitverse.adoct.parser.golden;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertTrue;

/** Списки: ul -> '*', ol -> '.', вложенность увеличивает глубину маркера. */
public class ListParserTest extends AbstractConvertParserTest {

    @Test
    public void unorderedListUsesStarMarker() throws IOException {
        String out = convert("<ul><li>раз</li><li>два</li></ul>");
        assertTrue(out.contains("* раз"));
        assertTrue(out.contains("* два"));
    }

    @Test
    public void orderedListUsesDotMarker() throws IOException {
        String out = convert("<ol><li>первый</li><li>второй</li></ol>");
        assertTrue(out.contains(". первый"));
        assertTrue(out.contains(". второй"));
    }

    @Test
    public void nestedListIncreasesMarkerDepth() throws IOException {
        String out = convert("<ul><li>верх<ul><li>низ</li></ul></li></ul>");
        assertTrue(out.contains("* верх"));
        assertTrue(out.contains("** низ"));
    }
}
