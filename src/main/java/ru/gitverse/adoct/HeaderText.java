package ru.gitverse.adoct;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;

public class HeaderText {

    public static String getHeader(Path endPointFileSource) throws IOException {

        Document document = Jsoup.parse(endPointFileSource, UTF_8.name());
        return Optional.of(document.select("header.ht-content-header h1"))
                .map(Elements::text)
                .orElseThrow(() -> new RuntimeException("не найден атрибут в header->h1 в файле %s"
                        .formatted(endPointFileSource)));
    }
}
