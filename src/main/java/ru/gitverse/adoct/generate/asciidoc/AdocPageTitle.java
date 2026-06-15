package ru.gitverse.adoct.generate.asciidoc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Определяет заголовок страницы Confluence, соответствующей {@code .adoc}-файлу: первый заголовок
 * файла ({@code = Title}, иначе первый раздел), иначе — имя файла без расширения.
 *
 * <p>Единая логика для адресации страниц по заголовку: используется и при включениях
 * ({@link ConfluenceIncludeProcessor}), и при межфайловых перекрёстных ссылках в рендерере.
 */
public final class AdocPageTitle {

    private static final Pattern HEADING = Pattern.compile("^=+\\s+(.+?)\\s*$");

    private AdocPageTitle() {
    }

    public static String fromFileOrName(Path file, String fallbackTarget) {
        if (file != null && Files.isRegularFile(file)) {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    Matcher m = HEADING.matcher(line);
                    if (m.matches()) {
                        return plain(m.group(1));
                    }
                }
            } catch (IOException ignored) {
                // не смогли прочитать — упадём на имя файла ниже
            }
        }
        return fileName(fallbackTarget);
    }

    /** Инлайн-разметка AsciiDoc (constrained): {@code `код`}, {@code *жирный*}, {@code _курсив_} и т.п. */
    private static final Pattern INLINE_MARKUP = Pattern.compile(
            "(?U)(?<![\\p{Alnum}])([`*_#~^])(?=\\S)(.+?)(?<=\\S)\\1(?![\\p{Alnum}])");

    /** HTML-теги (напр. {@code <code>}, {@code <strong>}), которые отдаёт {@code getDoctitle()}. */
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    /**
     * Приводит заголовок к простому тексту для имени страницы Confluence: убирает HTML-теги (их даёт
     * {@code Document.getDoctitle()}/{@code Section.getTitle()}) и инлайн-разметку AsciiDoc (её
     * содержит сырая строка {@code = ...}), снимает экранирование сущностей. Нужен, чтобы имя
     * страницы и заголовок-цель межстраничных ссылок/включений совпадали и не содержали разметки.
     */
    public static String plain(String title) {
        if (title == null) {
            return "";
        }
        String s = HTML_TAG.matcher(title).replaceAll("");
        s = unescape(s);
        s = INLINE_MARKUP.matcher(s).replaceAll("$2");
        return s.trim();
    }

    private static String unescape(String s) {
        return s.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#8217;", "’")
                .replace("&#8230;", "…")
                .replace("&amp;", "&");
    }

    private static String fileName(String target) {
        String name = Path.of(target).getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
