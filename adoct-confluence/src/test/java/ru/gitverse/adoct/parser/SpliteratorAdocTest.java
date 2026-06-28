package ru.gitverse.adoct.parser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import ru.gitverse.adoct.parser.model.MetadataKey;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Разрезка большого документа по level-2 заголовкам на отдельные файлы + index с include::. */
public class SpliteratorAdocTest {

    private Path tmp;

    @Before
    public void setUp() throws IOException {
        tmp = Files.createTempDirectory("adoct-split-test");
    }

    @After
    public void tearDown() throws IOException {
        if (tmp != null && Files.exists(tmp)) {
            try (var walk = Files.walk(tmp)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
    }

    @Test
    public void splitsBySecondLevelHeadingsAndWritesIncludes() throws IOException {
        String source = String.join("\n",
                "= Заголовок документа",
                ":toc: macro",
                "",
                "Вводный абзац.",
                "",
                "== Раздел один",
                "",
                "текст один",
                "",
                "== Раздел два",
                "",
                "текст два");

        SpliteratorAdoc.saveSplit(tmp, "index.adoc", source, "==",
                Map.of(MetadataKey.IMAGE, "attache"));

        Path index = tmp.resolve("index.adoc");
        Path s1 = tmp.resolve("1_Раздел один.adoc");
        Path s2 = tmp.resolve("2_Раздел два.adoc");
        assertTrue(Files.exists(index));
        assertTrue(Files.exists(s1));
        assertTrue(Files.exists(s2));

        String indexContent = read(index);
        // Преамбула остаётся в index
        assertTrue(indexContent.contains("= Заголовок документа"));
        assertTrue(indexContent.contains("Вводный абзац."));
        // Разделы подключаются через include
        assertTrue(indexContent.contains("include::1_Раздел один.adoc[]"));
        assertTrue(indexContent.contains("include::2_Раздел два.adoc[]"));
        // Тела разделов уехали из index
        assertFalse(indexContent.contains("текст один"));

        String s1Content = read(s1);
        // Каждый кусок переустанавливает imagesdir и держит свой заголовок + тело
        assertTrue(s1Content.startsWith(":imagesdir: ./attache"));
        assertTrue(s1Content.contains("== Раздел один"));
        assertTrue(s1Content.contains("текст один"));
        assertFalse(s1Content.contains("текст два"));

        String s2Content = read(s2);
        assertTrue(s2Content.contains("== Раздел два"));
        assertTrue(s2Content.contains("текст два"));
    }

    @Test
    public void documentWithoutHeadingsStaysSingleFile() throws IOException {
        String source = String.join("\n", "= Только преамбула", "", "Один абзац без разделов.");

        SpliteratorAdoc.saveSplit(tmp, "index.adoc", source, "==",
                Map.of(MetadataKey.IMAGE, "attache"));

        try (var list = Files.list(tmp)) {
            assertEquals(1, list.count());
        }
        String indexContent = read(tmp.resolve("index.adoc"));
        assertTrue(indexContent.contains("Один абзац без разделов."));
        assertFalse(indexContent.contains("include::"));
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p).replace("\r\n", "\n");
    }
}
