package ru.gitverse.adoct.parser.golden;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertTrue;

/** Преамбула документа: заголовок, :toc:, :imagesdir:. */
public class DocumentPreambleParserTest extends AbstractConvertParserTest {

    @Test
    public void emitsDocumentPreamble() throws IOException {
        String out = convert("<p>тело</p>");
        assertTrue(out.contains("= Документ"));
        assertTrue(out.contains(":toc: macro"));
        assertTrue(out.contains(":imagesdir: ./attache"));
    }
}
