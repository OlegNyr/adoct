package io.github.adoct.parser;

import org.apache.commons.io.FilenameUtils;
import io.github.adoct.parser.model.MetadataKey;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Режет большой документ на файлы по заголовкам уровня 2. Формат-специфику (расширение файлов, директиву
 * вставки под-файла в корневой) держит {@link OutputFormat}: AsciiDoc использует {@code include::} и
 * {@code :imagesdir:}, Markdown — обычную ссылку (трансклюзии в GFM нет).
 */
public class SpliteratorAdoc {

    public static void saveSplit(Path destination, String fileName, String source, String split,
                                 Map<MetadataKey, Object> metadata, OutputFormat format) throws IOException {
        List<Item> res = new ArrayList<>();
        List<String> lines = source.lines().toList();
        Item main = new Item(fileName, new ArrayList<>());
        // main всегда в результате: документ без split-заголовков остаётся одним файлом
        // (иначе при отсутствии заголовков не записывалось бы ни одного файла — потеря всего вывода).
        res.add(main);
        Item cur = main;
        String prefix = split + " ";
        int index = 1;
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                String title = line.substring(prefix.length());
                String childName = index++ + "_" + FilenameUtils.normalize(title) + "." + format.extension();

                cur = new Item(childName, new ArrayList<>());
                res.add(cur);
                if (format == OutputFormat.ADOC) {
                    cur.content().add(":imagesdir: ./%s".formatted(metadata.get(MetadataKey.IMAGE)));
                }
                cur.content().add(line);
                main.content().add("");
                main.content().add(reference(childName, title, format));
            } else {
                cur.content().add(line);
            }
        }
        for (Item item : res) {
            Files.writeString(destination.resolve(item.fileName()),
                    String.join(System.lineSeparator(), item.content())
            );
        }
    }

    /** Ссылка на под-файл из корневого документа: AsciiDoc — {@code include::}, Markdown — обычная ссылка. */
    private static String reference(String childName, String title, OutputFormat format) {
        return format == OutputFormat.MD ? "- [%s](%s)".formatted(title, childName)
                : "include::%s[]".formatted(childName);
    }

    record Item(String fileName, List<String> content) {
    }
}
