package io.github.adoct.parser.ast;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Юнит-тесты {@link MarkdownWriter}: форма GFM-вывода, таблицы (pipe/HTML-фолбэк), эмфаза, экранирование. */
public class MarkdownWriterTest {

    private final MarkdownWriter writer = new MarkdownWriter();

    private static List<Inline> text(String s) {
        return List.of(new Inline.Text(s));
    }

    private static Block.Cell cell(String s) {
        return new Block.Cell(1, 1, false, text(s), null);
    }

    @Test
    public void headingAndParagraph() {
        String out = writer.write(List.of(
                new Block.Heading(1, text("Заголовок")),
                new Block.Paragraph(text("Абзац."))));
        assertEquals("## Заголовок\n\nАбзац.", out);
    }

    @Test
    public void inlineFormatting() {
        String out = writer.write(List.of(new Block.Paragraph(List.of(
                new Inline.Bold(text("жир")),
                new Inline.Text(" "),
                new Inline.Italic(text("кур")),
                new Inline.Text(" "),
                new Inline.Underline(text("подч"))))));
        assertEquals("**жир** *кур* <u>подч</u>", out);
    }

    @Test
    public void nbspAroundEmphasisIsHoistedOut() {
        // "** текст**" ломает GFM — nbsp выносится за маркеры
        String out = writer.write(List.of(new Block.Paragraph(List.of(
                new Inline.Bold(List.of(new Inline.Text(" текст ")))))));
        assertEquals(" **текст** ", out);
    }

    @Test
    public void simpleTableBecomesGfmPipeTable() {
        Block.Table table = new Block.Table("1a,1a",
                List.of(new Block.Row(List.of(cell("H1"), cell("H2")))),
                List.of(new Block.Row(List.of(cell("a"), cell("b")))));
        String out = writer.write(List.of(table));
        assertEquals("| H1 | H2 |\n| --- | --- |\n| a | b |", out);
    }

    @Test
    public void pipeInsideCellIsEscaped() {
        Block.Table table = new Block.Table("1a",
                List.of(new Block.Row(List.of(cell("H")))),
                List.of(new Block.Row(List.of(cell("a | b")))));
        String out = writer.write(List.of(table));
        assertTrue(out.contains("a \\| b"));
    }

    @Test
    public void colspanTableFallsBackToHtml() {
        Block.Cell spanned = new Block.Cell(2, 1, false, text("wide"), null);
        Block.Table table = new Block.Table("1a,1a",
                List.of(),
                List.of(new Block.Row(List.of(spanned))));
        String out = writer.write(List.of(table));
        assertTrue(out.startsWith("<table>"));
        assertTrue(out.contains("colspan=\"2\""));
    }

    @Test
    public void richCellFallsBackToHtml() {
        Block.Cell rich = new Block.Cell(1, 1, false, null,
                List.of(new Block.Paragraph(text("p1")), new Block.Paragraph(text("p2"))));
        Block.Table table = new Block.Table("1a", List.of(), List.of(new Block.Row(List.of(rich))));
        String out = writer.write(List.of(table));
        assertTrue(out.contains("<table>"));
        assertTrue(out.contains("p1<br>p2"));
    }

    @Test
    public void admonitionBecomesGfmAlert() {
        String out = writer.write(List.of(new Block.Admonition("NOTE", null,
                List.of(new Block.Paragraph(text("важно"))))));
        assertEquals("> [!NOTE]\n> важно", out);
    }

    @Test
    public void admonitionInsideCellUsesEmojiNotBlockquote() {
        Block.Cell rich = new Block.Cell(1, 1, false, null,
                List.of(new Block.Admonition("WARNING", null, List.of(new Block.Paragraph(text("осторожно"))))));
        Block.Table table = new Block.Table("1a", List.of(), List.of(new Block.Row(List.of(rich))));
        String out = writer.write(List.of(table));
        assertTrue(out.contains("⚠️ осторожно"));
        assertFalse(out.contains("> [!"));
    }

    @Test
    public void nestedListIndentation() {
        String out = writer.write(List.of(new Block.ItemList(false, List.of(
                new Block.ListItem(text("один"), List.of(
                        new Block.ItemList(false, List.of(
                                new Block.ListItem(text("вложенный"), List.of()))))),
                new Block.ListItem(text("два"), List.of())))));
        assertEquals("- один\n  - вложенный\n- два", out);
    }

    @Test
    public void taskListBecomesCheckboxes() {
        String out = writer.write(List.of(new Block.TaskList(List.of(
                new Block.TaskItem(true, text("готово")),
                new Block.TaskItem(false, text("в работе"))))));
        assertEquals("- [x] готово\n- [ ] в работе", out);
    }

    @Test
    public void codeBlockBecomesFence() {
        String out = writer.write(List.of(new Block.CodeBlock("java", null, "int x = 1;", null)));
        assertEquals("```java\nint x = 1;\n```", out);
    }

    @Test
    public void emptyLabelLinkBecomesAutolink() {
        String out = writer.write(List.of(new Block.Paragraph(List.of(
                new Inline.Link("https://jira/BROWSE-1", List.of())))));
        assertEquals("<https://jira/BROWSE-1>", out);
    }
}
