package ru.gitverse.adoct.parser.golden;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertTrue;

/** Заголовки h1..h4 -> уровни '=' AsciiDoc. */
public class HeaderParserTest extends AbstractConvertParserTest {

    @Test
    public void headerLevelsMapToAsciidocEquals() throws IOException {
        String out = convert("<h1>Один</h1><h2>Два</h2><h3>Три</h3>");
        // h1 печатает (n+1) символов '=': h1 -> '==', h2 -> '===', h3 -> '===='
        assertTrue(out.contains("== Один"));
        assertTrue(out.contains("=== Два"));
        assertTrue(out.contains("==== Три"));
    }
}
