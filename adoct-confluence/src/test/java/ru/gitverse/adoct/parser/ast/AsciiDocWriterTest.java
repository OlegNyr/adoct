package ru.gitverse.adoct.parser.ast;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.asciidoctor.log.LogRecord;
import org.asciidoctor.log.Severity;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Юнит-тесты {@link AsciiDocWriter}: форма вывода + синтаксическая валидность через AsciidoctorJ. */
public class AsciiDocWriterTest {

    private static Asciidoctor asciidoctor;
    private static final List<LogRecord> LOGS = new ArrayList<>();

    private final AsciiDocWriter writer = new AsciiDocWriter();

    @BeforeClass
    public static void start() {
        asciidoctor = Asciidoctor.Factory.create();
        asciidoctor.registerLogHandler(LOGS::add);
    }

    @AfterClass
    public static void stop() {
        if (asciidoctor != null) {
            asciidoctor.close();
        }
    }

    private static List<Inline> text(String s) {
        return List.of(new Inline.Text(s));
    }

    private void assertValid(String adoc) {
        LOGS.clear();
        asciidoctor.load(adoc, Options.builder().safe(SafeMode.UNSAFE).build());
        String severe = LOGS.stream()
                .filter(r -> r.getSeverity() == Severity.ERROR || r.getSeverity() == Severity.FATAL)
                .map(r -> r.getSeverity() + ": " + r.getMessage())
                .collect(Collectors.joining("\n"));
        assertTrue("Невалидный AsciiDoc:\n" + severe + "\n---\n" + adoc, severe.isEmpty());
    }

    @Test
    public void blocksSeparatedBySingleBlankLine() {
        String out = writer.write(List.of(
                new Block.Heading(1, text("Заголовок")),
                new Block.Paragraph(text("Первый абзац.")),
                new Block.Paragraph(text("Второй абзац."))));
        assertEquals("== Заголовок\n\nПервый абзац.\n\nВторой абзац.", out);
        // нет тройных переводов строк — DubleCaretPostProcesing не нужен
        assertTrue(!out.contains("\n\n\n"));
        assertValid(out);
    }

    @Test
    public void inlineFormatting() {
        String out = writer.write(List.of(new Block.Paragraph(List.of(
                new Inline.Text("обычный "),
                new Inline.Bold(text("жирный")),
                new Inline.Text(" "),
                new Inline.Italic(text("курсив")),
                new Inline.Text(" "),
                new Inline.Underline(text("подч"))))));
        assertEquals("обычный **жирный** __курсив__ [.underline]##подч##", out);
        assertValid(out);
    }

    @Test
    public void nestedList() {
        String out = writer.write(List.of(new Block.ItemList(false, List.of(
                new Block.ListItem(text("один"), List.of(
                        new Block.ItemList(false, List.of(
                                new Block.ListItem(text("вложенный"), List.of()))))),
                new Block.ListItem(text("два"), List.of())))));
        assertEquals("* один\n** вложенный\n* два", out);
        assertValid(out);
    }

    @Test
    public void tableCellsOnePerLine() {
        Block.Table table = new Block.Table("1a,1a",
                List.of(new Block.Row(List.of(
                        new Block.Cell(1, 1, false, text("H1"), null),
                        new Block.Cell(1, 1, false, text("H2"), null)))),
                List.of(new Block.Row(List.of(
                        new Block.Cell(1, 1, false, text("a"), null),
                        new Block.Cell(1, 1, false, text("b"), null)))));
        String out = writer.write(List.of(table));
        assertEquals("[cols=\"1a,1a\"]\n|===\n|H1 |H2\n\n|a\n|b\n\n|===", out);
        assertValid(out);
    }

    @Test
    public void admonitionWithRichBody() {
        String out = writer.write(List.of(new Block.Admonition("NOTE", null, List.of(
                new Block.Paragraph(text("осторожно")),
                new Block.Paragraph(text("второй абзац"))))));
        assertEquals("[NOTE]\n====\nосторожно\n\nвторой абзац\n====", out);
        assertValid(out);
    }

    @Test
    public void coloredInlineAtLineStartIsValid() {
        // строка, начинающаяся с [.role]## — проверяем, что AsciiDoc не путает её с блок-атрибутами
        String out = writer.write(List.of(new Block.Paragraph(List.of(
                new Inline.Colored("red", text("красный")),
                new Inline.Text(" хвост")))));
        assertValid(out);
    }
}
