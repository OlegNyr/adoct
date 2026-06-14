package ru.gitverse.adoct.parser.golden;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.asciidoctor.log.LogRecord;
import org.asciidoctor.log.Severity;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

/**
 * Round-trip валидация: вывод конвертера должен быть синтаксически корректным AsciiDoc.
 * <p>
 * Storage-фрагмент прогоняется через настоящий {@link ru.gitverse.adoct.parser.ConvertStorageToAdoc}
 * (как в остальных golden-тестах), а затем результат загружается реальным AsciidoctorJ.
 * Парсер AsciiDoctor очень терпим и почти никогда не бросает исключения, поэтому критерий —
 * отсутствие записей лога уровня {@code ERROR}/{@code FATAL} (битые таблицы, незакрытые блоки и т.п.).
 * <p>
 * Это страховка под рефакторинг: пока движок генерит «сырой текст», тест ловит ровно тот класс
 * поломок, ради которых существует пост-процессинг.
 */
public class RoundTripValidationParserTest extends AbstractConvertParserTest {

    private static Asciidoctor asciidoctor;
    private static final List<LogRecord> LOG_RECORDS = new ArrayList<>();

    @BeforeClass
    public static void startAsciidoctor() {
        asciidoctor = Asciidoctor.Factory.create();
        asciidoctor.registerLogHandler(LOG_RECORDS::add);
    }

    @AfterClass
    public static void stopAsciidoctor() {
        if (asciidoctor != null) {
            asciidoctor.close();
        }
    }

    @Test
    public void headingsAndInlineFormatting() throws IOException {
        assertRoundTrips(
                "<h1>Заголовок</h1>"
                + "<p>Текст со <strong>жирным</strong>, <i>курсивом</i> и <u>подчёркиванием</u>.</p>"
                + "<h2>Подзаголовок</h2><p>Ещё абзац.</p>");
    }

    @Test
    public void nestedLists() throws IOException {
        assertRoundTrips(
                "<ul><li>один<ul><li>вложенный</li></ul></li><li>два</li></ul>"
                + "<ol><li>первый</li><li>второй</li></ol>");
    }

    @Test
    public void tableWithHead() throws IOException {
        assertRoundTrips(
                "<table><thead><tr><th>Колонка1</th><th>Колонка2</th></tr></thead>"
                + "<tbody><tr><td>знач1</td><td>знач2</td></tr>"
                + "<tr><td>знач3</td><td>знач4</td></tr></tbody></table>");
    }

    @Test
    public void nestedTable() throws IOException {
        assertRoundTrips(
                "<table><thead><tr><th>Внешняя</th></tr></thead><tbody><tr><td>"
                + "<table><thead><tr><th>Внутренняя</th></tr></thead>"
                + "<tbody><tr><td>ячейка</td></tr></tbody></table>"
                + "</td></tr></tbody></table>");
    }

    @Test
    public void admonitionMacro() throws IOException {
        assertRoundTrips(
                "<ac:structured-macro ac:name=\"note\">"
                + "<ac:rich-text-body><p>осторожно</p></ac:rich-text-body></ac:structured-macro>");
    }

    @Test
    public void codeMacro() throws IOException {
        assertRoundTrips(
                "<ac:structured-macro ac:name=\"code\">"
                + "<ac:parameter ac:name=\"language\">java</ac:parameter>"
                + "<ac:plain-text-body>int x = 1;</ac:plain-text-body></ac:structured-macro>");
    }

    @Test
    public void kitchenSink() throws IOException {
        assertRoundTrips(
                "<h1>Документ</h1>"
                + "<p>Абзац с <strong>акцентом</strong>.</p>"
                + "<ul><li>пункт</li></ul>"
                + "<table><thead><tr><th>A</th><th>B</th></tr></thead>"
                + "<tbody><tr><td>1</td><td>2</td></tr></tbody></table>"
                + "<ac:structured-macro ac:name=\"note\">"
                + "<ac:rich-text-body><p>заметка</p></ac:rich-text-body></ac:structured-macro>"
                + "<ac:structured-macro ac:name=\"toc\"/>");
    }

    /** Конвертирует storage-фрагмент и проверяет, что AsciidoctorJ парсит результат без ERROR/FATAL. */
    private void assertRoundTrips(String storageBody) throws IOException {
        String adoc = convert(storageBody);

        LOG_RECORDS.clear();
        asciidoctor.load(adoc, Options.builder().safe(SafeMode.UNSAFE).build());

        List<LogRecord> severe = LOG_RECORDS.stream()
                .filter(r -> r.getSeverity() == Severity.ERROR || r.getSeverity() == Severity.FATAL)
                .toList();
        String details = severe.stream()
                .map(r -> r.getSeverity() + ": " + r.getMessage())
                .collect(Collectors.joining("\n"));
        assertTrue("AsciiDoc-вывод невалиден:\n" + details + "\n--- adoc ---\n" + adoc,
                severe.isEmpty());
    }
}
