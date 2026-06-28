package ru.gitverse.adoct.parser.golden;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Confluence-макросы (ac:structured-macro): jira, code, note/warning, toc, numberedheadings, expand, неизвестный. */
public class MacrosParserTest extends AbstractConvertParserTest {

    @Test
    public void jiraMacro() throws IOException {
        String out = convert(
                "<ac:structured-macro ac:name=\"jira\">"
                + "<ac:parameter ac:name=\"key\">ABC-123</ac:parameter></ac:structured-macro>");
        assertTrue(out.contains("link:https://jira.example.com/browse/ABC-123[]"));
    }

    @Test
    public void codeMacroEmitsSourceBlock() throws IOException {
        String out = convert(
                "<ac:structured-macro ac:name=\"code\">"
                + "<ac:parameter ac:name=\"language\">java</ac:parameter>"
                + "<ac:plain-text-body>int x = 1;</ac:plain-text-body></ac:structured-macro>");
        assertTrue(out.contains("[source, java]"));
        assertTrue(out.contains("----"));
        assertTrue(out.contains("int x = 1;"));
    }

    @Test
    public void codeMacroInfersJsonLanguage() throws IOException {
        String out = convert(
                "<ac:structured-macro ac:name=\"code\">"
                + "<ac:plain-text-body>{\"a\":1}</ac:plain-text-body></ac:structured-macro>");
        // без параметра language тело, начинающееся с '{', определяется как json
        assertTrue(out.contains("[source, json]"));
    }

    @Test
    public void noteMacroBecomesAdmonition() throws IOException {
        String out = convert(
                "<ac:structured-macro ac:name=\"note\">"
                + "<ac:rich-text-body><p>осторожно</p></ac:rich-text-body></ac:structured-macro>");
        assertTrue(out.contains("[NOTE]"));
        assertTrue(out.contains("===="));
        assertTrue(out.contains("осторожно"));
    }

    @Test
    public void warningMacroBecomesWarningAdmonition() throws IOException {
        String out = convert(
                "<ac:structured-macro ac:name=\"warning\">"
                + "<ac:rich-text-body><p>опасно</p></ac:rich-text-body></ac:structured-macro>");
        assertTrue(out.contains("[WARNING]"));
        assertTrue(out.contains("опасно"));
    }

    @Test
    public void tipMacroBecomesTipAdmonition() throws IOException {
        String out = convert(
                "<ac:structured-macro ac:name=\"tip\">"
                + "<ac:rich-text-body><p>подсказка</p></ac:rich-text-body></ac:structured-macro>");
        assertTrue(out.contains("[TIP]"));
        assertTrue(out.contains("подсказка"));
    }

    @Test
    public void infoMacroBecomesNoteAdmonition() throws IOException {
        String out = convert(
                "<ac:structured-macro ac:name=\"info\">"
                + "<ac:rich-text-body><p>инфо</p></ac:rich-text-body></ac:structured-macro>");
        assertTrue(out.contains("[NOTE]"));
        assertTrue(out.contains("инфо"));
    }

    @Test
    public void tocMacro() throws IOException {
        String out = convert("<ac:structured-macro ac:name=\"toc\"/>");
        assertTrue(out.contains("toc::[]"));
    }

    @Test
    public void numberedHeadingsMacroEnablesSectnums() throws IOException {
        String out = convert(
                "<ac:structured-macro ac:name=\"numberedheadings\">"
                + "<ac:rich-text-body><h1>Глава</h1></ac:rich-text-body></ac:structured-macro>");
        assertTrue(out.contains(":sectnums:"));
        assertTrue(out.contains("== Глава"));
    }

    @Test
    public void expandMacroEmitsSubHeaderFromTitle() throws IOException {
        String out = convert(
                "<ac:structured-macro ac:name=\"expand\">"
                + "<ac:parameter ac:name=\"title\">Подробности</ac:parameter>"
                + "<ac:rich-text-body><p>скрытый текст</p></ac:rich-text-body></ac:structured-macro>");
        // topHeader по умолчанию 0 -> printHeader(1) -> '=='
        assertTrue(out.contains("== Подробности"));
        assertTrue(out.contains("скрытый текст"));
    }

    @Test
    public void unknownMacroIsIgnored() throws IOException {
        String out = convert(
                "<p>до</p>"
                + "<ac:structured-macro ac:name=\"totally-unknown-macro\">"
                + "<ac:rich-text-body><p>тело</p></ac:rich-text-body></ac:structured-macro>"
                + "<p>после</p>");
        // неизвестный макрос только логируется и ничего не печатает
        assertTrue(out.contains("до"));
        assertTrue(out.contains("после"));
        assertFalse(out.contains("тело"));
    }
}
