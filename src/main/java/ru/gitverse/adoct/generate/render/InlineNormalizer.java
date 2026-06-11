package ru.gitverse.adoct.generate.render;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import ru.gitverse.adoct.generate.asciidoc.AdocPageTitle;
import ru.gitverse.adoct.generate.asciidoc.AnchorIndex;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Нормализует инлайн-HTML от AsciiDoctor под Confluence storage format. Применяется к инлайн-
 * фрагментам (текст абзацев, элементов списков, ячеек, термины/описания {@code <dl>}) — не к
 * собранным макросам {@code <ac:...>}/CDATA. Делает:
 * <ol>
 *   <li>приводит фрагмент к строгому XML через jsoup: само-закрывает void-элементы
 *       ({@code <br>} → {@code <br/>}) и чинит перекрытие/незакрытость тегов, иначе Confluence
 *       отвергает страницу как невалидный XHTML;</li>
 *   <li>межфайловые ссылки {@code <<file.adoc#id,..>>} → ссылка на страницу-цель по её заголовку;</li>
 *   <li>якоря {@code [[id]]} ({@code <a id="id">}) → макрос {@code anchor};</li>
 *   <li>внутренние ссылки {@code <<id,..>>} ({@code <a href="#id">}) → {@code <ac:link ac:anchor=..>}
 *       (на другую страницу, если якорь объявлен в другом файле набора);</li>
 *   <li>ссылки на существующие локальные файлы → загрузка во вложения + ссылка на attach; внешние URL
 *       не трогаются.</li>
 * </ol>
 */
final class InlineNormalizer {

    /** Инлайн-якорь от {@code [[id]]}: AsciiDoctor отдаёт пустой {@code <a id="...">}. */
    private static final Pattern ANCHOR_DEF = Pattern.compile("<a id=\"([^\"]*)\"\\s*></a>");

    /** Внутренняя перекрёстная ссылка от {@code <<id,текст>>}: {@code <a href="#id">текст</a>}. */
    private static final Pattern INTERNAL_LINK = Pattern.compile("<a href=\"#([^\"]+)\">(.*?)</a>");

    /** Межфайловая ссылка от {@code <<file.adoc#id,текст>>}: {@code <a href="file.adoc#id">текст</a>}. */
    private static final Pattern CROSS_DOC_LINK = Pattern.compile(
            "<a href=\"([^\"#]+\\.adoc)(?:#([^\"]+))?\">(.*?)</a>", Pattern.CASE_INSENSITIVE);

    /** Любая оставшаяся ссылка {@code <a href="X">текст</a>} (после .adoc и якорей). */
    private static final Pattern FILE_LINK = Pattern.compile("<a href=\"([^\"]+)\">(.*?)</a>");

    /** URL со схемой ({@code http:}, {@code https:}, {@code mailto:} и т.п.) — такие ссылки не трогаем. */
    private static final Pattern URL_SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.\\-]*:");

    private final Path baseDir;
    private final AnchorIndex anchorIndex;
    private final Path currentFile;

    InlineNormalizer(Path baseDir, AnchorIndex anchorIndex, Path currentFile) {
        this.baseDir = baseDir;
        this.anchorIndex = anchorIndex == null ? AnchorIndex.empty() : anchorIndex;
        this.currentFile = currentFile == null ? null : currentFile.toAbsolutePath().normalize();
    }

    /** Нормализует фрагмент; найденные локальные файлы добавляет в {@code attachments}. */
    String normalize(String html, List<Path> attachments) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String s = wellFormed(html);
        s = replaceAll(CROSS_DOC_LINK, s, m -> crossDocLink(m.group(1), m.group(2), m.group(3)));
        s = replaceAll(ANCHOR_DEF, s, m -> StorageFormat.anchorMacro(m.group(1)));
        s = replaceAll(INTERNAL_LINK, s, m -> internalLink(m.group(1), m.group(2)));
        s = replaceAll(FILE_LINK, s, m -> fileLink(m.group(1), m.group(2), m.group(), attachments));
        return s;
    }

    /**
     * Чинит инлайн-HTML до строго валидного XHTML-фрагмента средствами jsoup: закрывает void-теги и
     * перебалансирует перекрывающиеся/незакрытые теги. Работает только над стандартными HTML-тегами
     * AsciiDoctor (на этом этапе ещё нет наших {@code <ac:...>}/CDATA).
     */
    private static String wellFormed(String html) {
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .prettyPrint(false)
                .escapeMode(Entities.EscapeMode.xhtml)
                .charset(StandardCharsets.UTF_8);
        return doc.body().html();
    }

    /** Межфайловая ссылка на другую страницу (по заголовку файла-цели), опционально с якорем. */
    private String crossDocLink(String path, String anchor, String body) {
        String title = AdocPageTitle.fromFileOrName(resolveDoc(path), path);
        String text = body == null || body.isBlank() ? title : body;
        return StorageFormat.pageLink(title, anchor, text);
    }

    /**
     * Ссылка от {@code <a href="#id">}. Если якорь {@code id} объявлен в другом файле набора — это
     * ссылка на другую страницу Confluence (с якорем на ней); иначе — якорь в пределах текущей страницы.
     */
    private String internalLink(String anchor, String body) {
        String text = body == null || body.isBlank() ? anchor : body;
        AnchorIndex.Target target = anchorIndex.lookup(anchor);
        if (target != null && !target.file().equals(currentFile)) {
            return StorageFormat.pageLink(target.title(), anchor, text);
        }
        return StorageFormat.anchorLink(anchor, text);
    }

    /**
     * Оставшаяся {@code <a href>} (после обработки {@code .adoc}-ссылок и якорей). Относительная ссылка
     * на существующий локальный файл → файл во вложения + ссылка на attach; иначе (внешний URL со схемой
     * или {@code //host}, либо несуществующий файл) оставляем ссылку как есть.
     */
    private String fileLink(String href, String body, String original, List<Path> attachments) {
        if (URL_SCHEME.matcher(href).find() || href.startsWith("//")) {
            return original;
        }
        Path resolved = resolveDoc(href);
        if (!Files.isRegularFile(resolved)) {
            return original;
        }
        attachments.add(resolved);
        String fileName = resolved.getFileName().toString();
        String text = body == null || body.isBlank() ? fileName : body;
        return StorageFormat.attachmentLink(fileName, text);
    }

    private Path resolveDoc(String path) {
        Path p = Path.of(path);
        return baseDir == null ? p : baseDir.resolve(p).normalize();
    }

    /** {@link Matcher#replaceAll(Function)} с безопасным экранированием замены. */
    private static String replaceAll(Pattern pattern, String input, Function<Matcher, String> replacer) {
        Matcher m = pattern.matcher(input);
        StringBuilder sb = new StringBuilder(input.length() + 16);
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(replacer.apply(m)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
