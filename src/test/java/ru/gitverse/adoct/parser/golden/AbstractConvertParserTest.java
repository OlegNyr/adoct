package ru.gitverse.adoct.parser.golden;

import org.junit.After;
import org.junit.Before;
import ru.gitverse.adoct.ConvertStorageToAdoc;
import ru.gitverse.adoct.MetadataKey;
import ru.gitverse.adoct.post.DubleCaretPostProcesing;
import ru.gitverse.adoct.post.TableCompactPostProcesing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * База для golden-master тестов движка {@link ConvertStorageToAdoc} (storage-HTML Confluence -> AsciiDoc).
 * <p>
 * Прогон идёт через настоящий публичный API с теми же пост-процессорами, что и {@code DispatcherPage},
 * а не через внутренние хендлеры — так фиксируется реальный конвейер целиком, включая порядок диспетчеризации.
 * Результат читается из {@code index.adoc} и нормализуется к {@code \n}, чтобы ассерты не зависели от ОС.
 * <p>
 * Конкретные наборы проверок разнесены по подклассам по типам разметки (заголовки, списки, таблицы, ссылки, макросы и т.д.).
 */
public abstract class AbstractConvertParserTest {

    protected Path tmp;

    @Before
    public void setUp() throws IOException {
        tmp = Files.createTempDirectory("adoct-parser-test");
        Files.createDirectories(tmp.resolve("files"));
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

    /** Прогоняет storage-фрагмент через конвертер и возвращает содержимое index.adoc (нормализованное к \n). */
    protected String convert(String storageBody) throws IOException {
        return convert(storageBody, Map.of());
    }

    protected String convert(String storageBody, Map<MetadataKey, Object> extra) throws IOException {
        Map<MetadataKey, Object> metadata = new HashMap<>();
        metadata.put(MetadataKey.TITLE, "Документ");
        metadata.put(MetadataKey.IMAGE, "attache");
        metadata.put(MetadataKey.ATTACH_FOLDER_NAME, "attache");
        metadata.put(MetadataKey.DESTINATION_FOLDER, tmp);
        metadata.put(MetadataKey.FILES_FOLDER, tmp.resolve("files"));
        metadata.put(MetadataKey.FILES_FOLDER_NAME, "files");
        metadata.putAll(extra);

        ConvertStorageToAdoc converter = new ConvertStorageToAdoc(storageBody, null, tmp);
        converter.setProcesings(List.of(new DubleCaretPostProcesing(), new TableCompactPostProcesing()));
        converter.convert(metadata, tmp);

        return Files.readString(tmp.resolve("index.adoc")).replace("\r\n", "\n");
    }
}
