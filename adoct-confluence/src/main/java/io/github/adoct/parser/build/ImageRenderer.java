package io.github.adoct.parser.build;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import io.github.adoct.parser.ast.Inline;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Рендер картинок в формат-нейтральный узел {@link Inline.Image} (writer сам решает, как отрисовать
 * его в AsciiDoc/Markdown). Логика перенесена из {@code ParseImg}/{@code ParseImgAcc}:
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

    /** {@code <img>}: копирует файл в папку картинок и возвращает узел картинки (alt — из текста тега). */
    @SneakyThrows
    public Inline.Image img(Element el, String alt) {
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
        return new Inline.Image(filename, emptyToNull(alt), null, null);
    }

    /** {@code <ac:image>}: узел картинки с alt/width/height (без копирования файла). */
    public static Inline.Image acImage(Element e, String alt) {
        String filename = e.getElementsByTag("ri:attachment").attr("ri:filename");
        return new Inline.Image(filename, emptyToNull(alt), emptyToNull(e.attr("ac:width")),
                emptyToNull(e.attr("ac:height")));
    }

    private static String emptyToNull(String s) {
        return StringUtils.isEmpty(s) ? null : s;
    }
}
