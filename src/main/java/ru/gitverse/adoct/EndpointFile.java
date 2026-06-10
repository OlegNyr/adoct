package ru.gitverse.adoct;

import lombok.SneakyThrows;
import org.apache.commons.codec.net.URLCodec;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;

public class EndpointFile {
    private static final URLCodec URL_CODEC = new URLCodec();

    @SneakyThrows
    public static Path getEndpointFile(Path tempDirectory) {
        Path indexHtml = tempDirectory.resolve("index.html");
        Document document = Jsoup.parse(indexHtml, UTF_8.name());
        Path endPoint = Optional.of(document.select("body a"))
                .filter(e -> e.size() == 1)
                .map(a -> a.attr("href"))
                .map(EndpointFile::encodeUrl)
                .map(tempDirectory::resolve)
                .orElseThrow(() -> new RuntimeException("не найден атрибут в body->a->@href в файле %s".formatted(indexHtml)));
        if (!Files.exists(endPoint)) {
            throw new RuntimeException("файл эндпоинта не найден");
        }
        return endPoint;
    }

    @SneakyThrows
    public static String encodeUrl(String str) {
        return URL_CODEC.decode(str);
    }
}
