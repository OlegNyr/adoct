package io.github.adoct.parser.golden;

import org.junit.Assume;
import org.junit.Test;
import io.github.adoct.parser.OutputFormat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Markdown-двойник {@link StorageFixtureGoldenTest}: те же storage-фикстуры, но эталон-снапшот
 * {@code *.md}. Один вход — два эталона ({@code *.adoc} + {@code *.md}) фиксирует одинаковое поведение
 * форматов. Эталон создаётся автоматически, если его нет или задан {@code -DupdateSnapshots=true}.
 */
public class MarkdownFixtureGoldenTest extends AbstractConvertParserTest {

    private static final Path FIXTURES = Path.of("src", "test", "resources", "storage");
    private static final boolean UPDATE = Boolean.getBoolean("updateSnapshots");

    @Test
    public void fixturesMatchMarkdownSnapshots() throws IOException {
        List<Path> fixtures = fixtures();
        Assume.assumeFalse("Нет фикстур в " + FIXTURES, fixtures.isEmpty());

        List<String> failures = new ArrayList<>();
        for (Path fixture : fixtures) {
            String md = convert(Files.readString(fixture), java.util.Map.of(), OutputFormat.MD);

            Path expected = sibling(fixture);
            if (UPDATE || Files.notExists(expected)) {
                Files.writeString(expected, md);
            } else {
                String exp = Files.readString(expected).replace("\r\n", "\n");
                if (!exp.equals(md)) {
                    failures.add(fixture.getFileName() + " → md-снапшот разошёлся (обновить: -DupdateSnapshots=true)");
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
        String name = fixture.getFileName().toString().replace(".storage.html", ".md");
        return fixture.resolveSibling(name);
    }
}
