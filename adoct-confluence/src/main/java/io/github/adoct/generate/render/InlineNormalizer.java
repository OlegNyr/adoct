package io.github.adoct.generate.render;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import io.github.adoct.generate.asciidoc.AdocPageTitle;
import io.github.adoct.generate.asciidoc.AnchorIndex;

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

    // Во всех паттернах ссылок после значения href допускаем произвольные атрибуты ({@code [^>]*}):
    // AsciiDoctor добавляет к тегу {@code class="bare"} (для {@code link:x[]} без текста) или
    // {@code class="role"} (для {@code link:x[Текст,role=...]}), и без этого межфайловые .adoc-ссылки
    // не распознавались и утекали в обычный href.

    /** Внутренняя перекрёстная ссылка от {@code <<id,текст>>}: {@code <a href="#id">текст</a>}. */
    private static final Pattern INTERNAL_LINK = Pattern.compile("<a href=\"#([^\"]+)\"[^>]*>(.*?)</a>");

    /** Межфайловая ссылка от {@code <<file.adoc#id,текст>>}: {@code <a href="file.adoc#id">текст</a>}. */
    private static final Pattern CROSS_DOC_LINK = Pattern.compile(
            "<a href=\"([^\"#]+\\.adoc)(?:#([^\"]+))?\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE);

    /** Любая оставшаяся ссылка {@code <a href="X">текст</a>} (после .adoc и якорей). */
    private static final Pattern FILE_LINK = Pattern.compile("<a href=\"([^\"]+)\"[^>]*>(.*?)</a>");

    /**
     * Моноширинное упоминание файла набора: {@code `file.adoc`} → {@code <code>file.adoc</code>}. Если
     * путь указывает на существующий {@code .adoc} проекта — оборачиваем в ссылку на его страницу (сам
     * {@code <code>} остаётся телом ссылки). В Confluence «.adoc» не существует — там страница.
     */
    private static final Pattern CODE_ADOC = Pattern.compile(
            "<code>([^<>]+\\.adoc)(?:#([^<>]+))?</code>", Pattern.CASE_INSENSITIVE);

    /** URL со схемой ({@code http:}, {@code https:}, {@code mailto:} и т.п.) — такие ссылки не трогаем. */
    private static final Pattern URL_SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.\\-]*:");

    /** Инлайн-картинка от {@code image:x[]}: AsciiDoctor отдаёт {@code <img src=... alt=...>}. */
    private static final Pattern IMG_TAG = Pattern.compile("<img\\b[^>]*?/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMG_SRC = Pattern.compile("\\bsrc=\"([^\"]*)\"");
    private static final Pattern IMG_ALT = Pattern.compile("\\balt=\"([^\"]*)\"");

    private final Path baseDir;
    private final AnchorIndex anchorIndex;
    private final Path currentFile;
    private final String spaceKey;
    private final Function<Path, String> pageTitleResolver;
    private final Path rootDir;

    InlineNormalizer(Path baseDir, AnchorIndex anchorIndex, Path currentFile, String spaceKey,
                     Function<Path, String> pageTitleResolver, Path rootDir) {
        this.baseDir = baseDir;
        this.anchorIndex = anchorIndex == null ? AnchorIndex.empty() : anchorIndex;
        this.currentFile = currentFile == null ? null : currentFile.toAbsolutePath().normalize();
        this.spaceKey = spaceKey == null ? "" : spaceKey;
        this.pageTitleResolver = pageTitleResolver;
        this.rootDir = rootDir == null ? null : rootDir.toAbsolutePath().normalize();
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
        s = replaceAll(CODE_ADOC, s, m -> codeAdocLink(m.group(1), m.group(2), m.group()));
        s = replaceAll(IMG_TAG, s, m -> inlineImage(m.group(), attachments));
        return s;
    }

    /**
     * Инлайн-картинка {@code <img src=...>}. Внешний URL → {@code ac:image} с {@code ri:url}; существующий
     * локальный файл → файл во вложения + {@code ac:image} с {@code ri:attachment}; несуществующий локальный
     * путь — оставляем тег как есть. {@code src} уже содержит {@code imagesdir} (его подставил AsciiDoctor).
     */
    private String inlineImage(String imgTag, List<Path> attachments) {
        String src = attribute(IMG_SRC, imgTag);
        if (src == null || src.isBlank()) {
            return imgTag;
        }
        String alt = attribute(IMG_ALT, imgTag);
        if (URL_SCHEME.matcher(src).find() || src.startsWith("//")) {
            return StorageFormat.inlineImageUrl(src, alt);
        }
        Path resolved = resolveDoc(src);
        if (!Files.isRegularFile(resolved)) {
            return imgTag;
        }
        attachments.add(resolved);
        return StorageFormat.inlineImageAttachment(resolved.getFileName().toString(), alt);
    }

    private static String attribute(Pattern pattern, String tag) {
        Matcher m = pattern.matcher(tag);
        return m.find() ? m.group(1) : null;
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
        String title = pageTitleFor(resolveAdoc(path), path);
        return StorageFormat.pageLink(title, anchor, spaceKey, crossDocText(body, title, path, anchor));
    }

    /**
     * Моноширинное упоминание {@code <code>file.adoc</code>}. Если путь — существующий {@code .adoc}
     * набора, заменяем на ссылку на его страницу с текстом = заголовком этой страницы (имя файла в
     * тексте не показываем — в Confluence «.adoc» нет, есть страница). Иначе (внешнее имя/пример) —
     * оставляем как код.
     */
    private String codeAdocLink(String path, String anchor, String codeHtml) {
        Path file = resolveAdoc(path);
        if (!Files.isRegularFile(file)) {
            return codeHtml;
        }
        String title = pageTitleFor(file, path);
        return StorageFormat.pageLink(title, anchor, spaceKey, StorageFormat.escapeText(title));
    }

    /**
     * Резолвит {@code .adoc}-цель ссылки: сперва относительно папки текущего файла, затем относительно
     * корня публикации (в доках пути к другим страницам часто задают от корня набора). Возвращает
     * существующий файл, если нашли, иначе — base-вариант (для fallback-заголовка по имени файла).
     */
    private Path resolveAdoc(String path) {
        Path fromBase = resolveDoc(path);
        if (Files.isRegularFile(fromBase)) {
            return fromBase;
        }
        if (rootDir != null) {
            Path fromRoot = rootDir.resolve(path).normalize();
            if (Files.isRegularFile(fromRoot)) {
                return fromRoot;
            }
        }
        return fromBase;
    }

    /**
     * Заголовок страницы-цели: сперва через резолвер (реальный заголовок Confluence по
     * {@code :confluency-id:} файла), иначе — заголовок из самого {@code .adoc} ({@link AdocPageTitle}).
     * Резолвер нужен, потому что при обновлении существующей страницы её заголовок в Confluence мы не
     * меняем — и {@code = Заголовок} в файле может с ним расходиться.
     */
    private String pageTitleFor(Path file, String path) {
        if (pageTitleResolver != null) {
            String real = pageTitleResolver.apply(file);
            if (real != null && !real.isBlank()) {
                return real;
            }
        }
        return AdocPageTitle.fromFileOrName(file, path);
    }

    /**
     * Текст межстраничной ссылки: явный текст ({@code link:x[Текст]}) — как есть; пустой или равный сырой
     * цели ({@code link:x[]} даёт {@code class="bare"} и телом сам путь) → заголовок страницы-цели, чтобы
     * ссылка показывала имя страницы, а не {@code index.adoc}.
     */
    private static String crossDocText(String body, String title, String path, String anchor) {
        if (body == null || body.isBlank()) {
            return title;
        }
        String rawTarget = anchor == null || anchor.isBlank() ? path : path + "#" + anchor;
        return body.trim().equals(rawTarget) ? title : body;
    }

    /**
     * Ссылка от {@code <a href="#id">}. Если якорь {@code id} объявлен в другом файле набора — это
     * ссылка на другую страницу Confluence (с якорем на ней); иначе — якорь в пределах текущей страницы.
     */
    private String internalLink(String anchor, String body) {
        String text = body == null || body.isBlank() ? anchor : body;
        AnchorIndex.Target target = anchorIndex.lookup(anchor);
        if (target != null && !target.file().equals(currentFile)) {
            return StorageFormat.pageLink(target.title(), anchor, spaceKey, text);
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
