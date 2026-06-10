package ru.gitverse.adoct;

import com.intellij.codeInsight.template.emmet.EmmetPreviewUtil;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SpliteratorAdoc {


    public static void saveSplit(Path destination, String fileName, String source, String split,
                                 Map<MetadataKey, Object> metadata) throws IOException {
        List<Item> res = new ArrayList<>();
        List<String> lines = source.lines().toList();
        Item main = new Item(fileName, new ArrayList<>());
        Item cur = main;
        String prefix = split + " ";
        int index = 1;
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                String title = line.substring(prefix.length());
                res.add(cur);
                String fileName1 = index++ + "_" + FilenameUtils.normalize(title) + ".adoc";

                cur = new Item(fileName1, new ArrayList<>());
                cur.content().add(":imagesdir: ./%s".formatted(metadata.get(MetadataKey.IMAGE)));
                cur.content().add(line);
                main.content().add("");
                main.content().add("include::%s[]".formatted(cur.fileName()));
            } else {
                cur.content().add(line);
            }
        }
        if (cur != main) {
            res.add(cur);
        }
        for (Item item : res) {
            Files.writeString(destination.resolve(item.fileName()),
                    String.join(System.lineSeparator(), item.content())
            );
        }
    }

    record Item(String fileName, List<String> content) {
    }
}
