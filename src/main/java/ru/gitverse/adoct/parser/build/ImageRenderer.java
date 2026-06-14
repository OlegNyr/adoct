package ru.gitverse.adoct.parser.build;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Рендер картинок в тело макроса {@code файл[параметры]} (префикс {@code image:}/{@code image::}
 * добавляет вызывающий — инлайн или блок). Логика перенесена из {@code ParseImg}/{@code ParseImgAcc}:
 * {@code <img>} копирует файл из выгрузки в папку картинок, {@code <ac:image>} — нет.
 */
@Slf4j
public final class ImageRenderer {

    private final Path source;
    private final Path destination;

    public ImageRenderer(Path source, Path destination) {
        this.source = source;
        this.destination = destination;
    }

    /** {@code <img>}: копирует файл и возвращает {@code файл[alt]}. */
    @SneakyThrows
    public String img(Element el, String alt) {
        String filenameRaw = el.attr("src");
        String filename = Path.of(filenameRaw).getFileName().toString();

        if (source != null) {
            Path sourceFile = source.resolve(filenameRaw);
            if (Files.exists(sourceFile)) {
                Path target = destination.resolve(filename);
                Files.createDirectories(target.getParent());
                Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                log.warn("Not found file {}", sourceFile);
            }
        }
        return "%s[%s]".formatted(filename, alt == null ? "" : alt);
    }

    /** {@code <ac:image>}: возвращает {@code файл[alt,width,height]} без копирования. */
    public static String acImage(Element e, String alt) {
        String filename = e.getElementsByTag("ri:attachment").attr("ri:filename");
        List<String> params = new ArrayList<>();
        if (StringUtils.isNotEmpty(alt)) {
            params.add("alt=%s".formatted(alt));
        }
        if (StringUtils.isNotEmpty(e.attr("ac:width"))) {
            params.add("width=%s".formatted(e.attr("ac:width")));
        }
        if (StringUtils.isNotEmpty(e.attr("ac:height"))) {
            params.add("height=%s".formatted(e.attr("ac:height")));
        }
        return "%s[%s]".formatted(filename, String.join(",", params));
    }
}
