package ru.gitverse.adoct.parser.golden;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.asciidoctor.log.LogRecord;
import org.asciidoctor.log.Severity;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

/**
 * Файловый golden-харнесс по реальным/синтетическим storage-страницам.
 * <p>
 * Для каждого {@code src/test/resources/storage/*.storage.html}: конвертирует через настоящий конвейер,
 * (1) грузит результат AsciidoctorJ и падает на ERROR/FATAL (валидность), (2) сверяет с эталоном-снапшотом
 * {@code *.adoc} рядом. Эталон создаётся автоматически, если его нет или задан {@code -DupdateSnapshots=true}
 * (тогда тест перезаписывает эталоны — удобно после намеренного изменения вывода).
 * <p>
 * Сюда же кладём анонимизированные реальные страницы из {@code source/body.storage.html} — это самые
 * представительные фикстуры.
 */
public class StorageFixtureGoldenTest extends AbstractConvertParserTest {

    private static final Path FIXTURES = Path.of("src", "test", "resources", "storage");
    private static final boolean UPDATE = Boolean.getBoolean("updateSnapshots");

    private static Asciidoctor asciidoctor;
    private static final List<LogRecord> LOGS = new ArrayList<>();

    @BeforeClass
    public static void startAsciidoctor() {
        asciidoctor = Asciidoctor.Factory.create();
        asciidoctor.registerLogHandler(LOGS::add);
    }

    @AfterClass
    public static void stopAsciidoctor() {
        if (asciidoctor != null) {
            asciidoctor.close();
        }
    }

    @Test
    public void fixturesConvertValidlyAndMatchSnapshots() throws IOException {
        List<Path> fixtures = fixtures();
        Assume.assumeFalse("Нет фикстур в " + FIXTURES, fixtures.isEmpty());

        List<String> failures = new ArrayList<>();
        for (Path fixture : fixtures) {
            String adoc = convert(Files.readString(fixture));

            String invalid = validate(adoc);
            if (!invalid.isEmpty()) {
                failures.add(fixture.getFileName() + " → невалидный AsciiDoc:\n" + invalid);
            }

            Path expected = sibling(fixture);
            if (UPDATE || Files.notExists(expected)) {
                Files.writeString(expected, adoc);
            } else {
                String exp = Files.readString(expected).replace("\r\n", "\n");
                if (!exp.equals(adoc)) {
                    failures.add(fixture.getFileName() + " → снапшот разошёлся (обновить: -DupdateSnapshots=true)");
                }
            }
        }
        assertTrue(String.join("\n\n", failures), failures.isEmpty());
    }

    private static List<Path> fixtures() throws IOException {
        if (Files.notExists(FIXTURES)) {
            return List.of();
        }
        try (var list = Files.list(FIXTURES)) {
            return list.filter(p -> p.getFileName().toString().endsWith(".storage.html")).sorted().toList();
        }
    }

    private static Path sibling(Path fixture) {
        String name = fixture.getFileName().toString().replace(".storage.html", ".adoc");
        return fixture.resolveSibling(name);
    }

    private static String validate(String adoc) {
        LOGS.clear();
        asciidoctor.load(adoc, Options.builder().safe(SafeMode.UNSAFE).build());
        return LOGS.stream()
                .filter(r -> r.getSeverity() == Severity.ERROR || r.getSeverity() == Severity.FATAL)
                .map(r -> r.getSeverity() + ": " + r.getMessage())
                .collect(Collectors.joining("\n"));
    }
}
