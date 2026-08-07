package io.github.adoct.parser;

import org.junit.Test;
import io.github.adoct.parser.model.MetadataKey;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link ConvertStorageToAdoc#usedAttachments()} — какие вложения тело страницы реально использует.
 * Набор нужен, чтобы не качать всё подряд, поэтому важны обе стороны: не потерять нужное и не
 * притащить лишнее.
 */
public class UsedAttachmentsTest {

    private static Set<String> used(String storage) {
        return new ConvertStorageToAdoc(storage, "").usedAttachments();
    }

    @Test
    public void picksUpImageAttachment() {
        Set<String> used = used("<p><ac:image><ri:attachment ri:filename=\"схема.png\"/></ac:image></p>");

        assertEquals(Set.of("схема.png"), used);
    }

    @Test
    public void picksUpAttachmentLink() {
        Set<String> used = used("<p><ac:link><ri:attachment ri:filename=\"договор.pdf\"/></ac:link></p>");

        assertEquals(Set.of("договор.pdf"), used);
    }

    @Test
    public void picksUpAllDrawioCandidates() {
        // DrawioRenderer ищет исходник среди пяти файлов — в набор должны попасть все
        Set<String> used = used("<ac:structured-macro ac:name=\"drawio\">"
                + "<ac:parameter ac:name=\"diagramName\">Архитектура</ac:parameter></ac:structured-macro>");

        assertEquals(Set.of("Архитектура", "Архитектура.png", "Архитектура.svg",
                "Архитектура.drawio.png", "Архитектура.drawio.svg"), used);
    }

    @Test
    public void picksUpAttachmentOfMacroOutsideRegistry() {
        // view-file в реестре макросов нет, но ссылка на файл в выводе остаётся — вложение нужно
        Set<String> used = used("<ac:structured-macro ac:name=\"view-file\">"
                + "<ac:parameter ac:name=\"name\"><ri:attachment ri:filename=\"презентация.pptx\"/>"
                + "</ac:parameter></ac:structured-macro>");

        assertTrue(used.toString(), used.contains("презентация.pptx"));
    }

    @Test
    public void picksUpPlainImgFileName() {
        Set<String> used = used("<p><img src=\"attache/вставка.png?version=2\"/></p>");

        assertEquals(Set.of("вставка.png"), used);
    }

    @Test
    public void ignoresMacroWithoutAttachments() {
        Set<String> used = used("<ac:structured-macro ac:name=\"code\">"
                + "<ac:plain-text-body>System.out.println();</ac:plain-text-body></ac:structured-macro>");

        assertTrue(used.toString(), used.isEmpty());
    }

    @Test
    public void ignoresEmptyBody() {
        assertTrue(used("").isEmpty());
    }

    /**
     * Связка фильтра с {@code DrawioRenderer}: он ищет исходник диаграммы среди пяти файлов, и если
     * набор кандидатов отстанет от этого списка, включённый фильтр молча не скачает нужный — а
     * диаграмма исчезнет из вывода без единой ошибки. Тест проходит по всем раскладкам, которые
     * рендерер понимает без разбора бинарного PNG.
     */
    @Test
    public void filterKeepsWhatDrawioRendererLooksFor() throws IOException {
        Set<String> used = used(drawio("Схема"));

        assertTrue(used.toString(), used.containsAll(Set.of(
                "Схема",                                // сырой drawio-XML во вложении
                "Схема.png", "Схема.svg",               // рендеры рядом с ним
                "Схема.drawio.png", "Схема.drawio.svg"  // уже готовые комбинированные файлы
        )));
    }

    @Test
    public void readyDrawioFileSurvivesTheFilterEndToEnd() throws IOException {
        Path destination = Files.createTempDirectory("adoct-drawio-test");
        try {
            Path attache = Files.createDirectories(destination.resolve("attache"));
            String storage = drawio("Схема");
            // В attache кладём ровно то, что пропустил бы фильтр — больше выгрузка ничего не скачает
            assertTrue(used(storage).contains("Схема.drawio.svg"));
            Files.writeString(attache.resolve("Схема.drawio.svg"), "<svg content=\"mxfile\"></svg>");

            ConvertStorageToAdoc converter = new ConvertStorageToAdoc(storage, "", destination);
            converter.convert(exportMetadata(destination, attache), attache);

            String adoc = Files.readString(destination.resolve("index.adoc")).replace("\r\n", "\n");
            assertTrue(adoc, adoc.contains("image::attache/Схема.drawio.svg[]"));
        } finally {
            deleteRecursively(destination);
        }
    }

    private static String drawio(String diagramName) {
        return "<ac:structured-macro ac:name=\"drawio\">"
                + "<ac:parameter ac:name=\"diagramName\">" + diagramName + "</ac:parameter>"
                + "</ac:structured-macro>";
    }

    /** Минимальные метаданные обычного (не in-memory) экспорта — как их собирает {@code DispatcherPage}. */
    private static Map<MetadataKey, Object> exportMetadata(Path destination, Path attache) throws IOException {
        Path files = Files.createDirectories(destination.resolve("files"));
        Map<MetadataKey, Object> metadata = new EnumMap<>(MetadataKey.class);
        metadata.put(MetadataKey.LINKS, Map.of());
        metadata.put(MetadataKey.TITLE, "Страница с диаграммой");
        metadata.put(MetadataKey.IMAGE, "attache");
        metadata.put(MetadataKey.ATTACH_FOLDER, attache);
        metadata.put(MetadataKey.ATTACH_FOLDER_NAME, "attache");
        metadata.put(MetadataKey.DESTINATION_FOLDER, destination);
        metadata.put(MetadataKey.FILES_FOLDER, files);
        metadata.put(MetadataKey.FILES_FOLDER_NAME, "files");
        metadata.put(MetadataKey.COLOR, Boolean.FALSE);
        return metadata;
    }

    private static void deleteRecursively(Path root) throws IOException {
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    @Test
    public void collectsEveryReferenceOfMixedBody() {
        Set<String> used = used("<h1>Заголовок</h1>"
                + "<p><ac:image><ri:attachment ri:filename=\"a.png\"/></ac:image></p>"
                + "<p><ac:link><ri:attachment ri:filename=\"b.pdf\"/></ac:link></p>"
                + "<ac:structured-macro ac:name=\"drawio\">"
                + "<ac:parameter ac:name=\"diagramName\">c</ac:parameter></ac:structured-macro>");

        assertTrue(used.containsAll(Set.of("a.png", "b.pdf", "c", "c.drawio.png")));
        assertFalse(used.contains("Заголовок"));
    }
}
