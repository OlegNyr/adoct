package io.github.adoct.parser;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import io.github.adoct.parser.ast.Block;
import io.github.adoct.parser.build.AstBuilder;
import io.github.adoct.parser.model.LinksAttachment;
import io.github.adoct.parser.model.LinksPage;
import io.github.adoct.parser.model.LinksUser;
import io.github.adoct.parser.model.LinksValue;
import io.github.adoct.parser.model.MetadataKey;

import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Конвертер Confluence storage-HTML → AsciiDoc или Markdown.
 * <p>
 * Парсит HTML через Jsoup, строит формат-нейтральное дерево {@link Block} ({@link AstBuilder}) и
 * сериализует его writer'ом выбранного {@link OutputFormat} (AsciiDoc по умолчанию). Дисциплину пустых
 * строк и раскладку таблиц держит writer, поэтому строковые пост-процессоры не нужны.
 */
public class ConvertStorageToAdoc {

    private final String content;
    private final Document document;
    private final String view;
    private final Path destination;
    /** Лениво разобранный {@link #view} — см. {@link #viewDocument()}. */
    private Document viewDocument;
    /** Дублировать ли вывод в stdout (отладка). По умолчанию выключено. */
    @Setter
    @Getter
    private boolean printToStdout = false;
    /** Целевой формат экспорта (AsciiDoc по умолчанию). */
    @Setter
    @Getter
    private OutputFormat format = OutputFormat.ADOC;

    public ConvertStorageToAdoc(String content, String view, Path destination) {
        this.content = content;
        this.document = Jsoup.parse(content, UTF_8.name());
        this.view = view;
        if (!Files.exists(destination) || !Files.isDirectory(destination)) {
            throw new IllegalArgumentException("Папка назначения не существует %s".formatted(destination));
        }
        this.destination = destination;
    }

    /** Конструктор для конвертации в память (без записи файлов): {@link #toAdoc(Map, Path)}. */
    public ConvertStorageToAdoc(String content, String view) {
        this.content = content;
        this.document = Jsoup.parse(content, UTF_8.name());
        this.view = view;
        this.destination = null;
    }

    @SneakyThrows
    public Set<String> getColors() {
        Elements elementsByTag = document.getAllElements();
        Set<String> styles = new HashSet<>();
        for (Element element : elementsByTag) {
            String style = element.attr("style");
            if (StringUtils.isNotEmpty(style)) {
                for (String s : StringUtils.split(style, ";")) {
                    if (s.startsWith("color")) {
                        styles.add(s);
                    }
                }
            }
        }
        return styles;
    }

    /**
     * Разобранный rendered view — самое тяжёлое тело страницы, поэтому парсим его лениво и один раз
     * на конвертацию (нужен и для резолва ссылок, и для картинок с чужих страниц).
     */
    private Document viewDocument() {
        if (viewDocument == null) {
            viewDocument = Jsoup.parse(view == null ? "" : view, UTF_8.name());
        }
        return viewDocument;
    }

    public Map<String, String> resolveLink() {
        Elements elements = viewDocument().getElementsByTag("a");
        Map<String, String> res = new HashMap<>();
        for (Element element : elements) {
            String link = element.attr("href");
            String title = element.text();
            if (StringUtils.isNoneBlank(link, title)) {
                res.put(title, link);
            }
        }
        return res;
    }

    @SneakyThrows
    public Set<LinksValue> getLinks() {
        List<LinksValue> res = new ArrayList<>();
        Elements tagLinks = document.getElementsByTag("ac:link");
        for (Element tagLink : tagLinks) {
            Elements tagUser = tagLink.getElementsByTag("ri:user");
            if (!tagUser.isEmpty()) {
                res.add(new LinksUser(tagUser.attr("ri:userkey")));
                continue;
            }
            Elements pageConfluence = tagLink.getElementsByTag("ri:page");
            if (!pageConfluence.isEmpty()) {
                Elements plain = tagLink.getElementsByTag("ac:plain-text-link-body");
                String title = plain.text();
                if (StringUtils.isBlank(title)) {
                    title = pageConfluence.attr("ri:content-title");
                }
                String anchor = tagLink.attr("ac:anchor");
                if (StringUtils.isNotEmpty(anchor)) {
                    title = title + "#" + anchor;
                }
                res.add(new LinksPage(title, pageConfluence.attr("ri:space-key")));
                continue;
            }
            Elements attachment = tagLink.getElementsByTag("ri:attachment");
            if (!attachment.isEmpty()) {
                res.add(new LinksAttachment(attachment.attr("ri:filename")));
            }
        }
        for (Element macro : document.getElementsByTag("ac:structured-macro")) {
            if (!"profile".equals(macro.attr("ac:name"))) {
                continue;
            }
            String userKey = macro.getElementsByTag("ri:user").attr("ri:userkey");
            if (StringUtils.isNotBlank(userKey)) {
                res.add(new LinksUser(userKey));
            }
        }
        return Set.copyOf(res);
    }

    /**
     * Имена файлов всех картинок-вложений тела страницы ({@code <ac:image><ri:attachment ri:filename>}).
     * Часть из них может лежать на другой странице (вложенный {@code <ri:page>}) — тогда в карте вложений
     * текущей страницы их нет, и URL нужно брать из rendered view ({@link #imageSources()}).
     */
    public Set<String> imageAttachments() {
        Set<String> res = new HashSet<>();
        for (Element image : document.getElementsByTag("ac:image")) {
            String filename = image.getElementsByTag("ri:attachment").attr("ri:filename");
            if (StringUtils.isNotBlank(filename)) {
                res.add(filename);
            }
        }
        return res;
    }

    /**
     * Имена вложений, на которые тело страницы реально ссылается, — чтобы не качать всё подряд.
     *
     * <p>Собирается сплошным сканом {@code ri:attachment} по всему документу, а не перечислением
     * контекстов ({@code ac:image}, {@code ac:link}, …): так автоматически попадают и параметры
     * макросов, которых нет в реестре ({@code view-file}, {@code multimedia}, {@code gallery} и
     * прочие плагинные) — их тело разворачивается как есть, и ссылка на файл в выводе остаётся.
     *
     * <p>Для {@code drawio}/{@code inc-drawio} добавляются <b>все</b> файлы, среди которых
     * {@code DrawioRenderer} ищет исходник диаграммы: имя без расширения, {@code .png}/{@code .svg}
     * рендеры и уже готовые комбинированные {@code .drawio.png}/{@code .drawio.svg}.
     *
     * <p>Имя вложения здесь — то, что написано в storage. Совпадает ли оно с реальным вложением
     * страницы, решает вызывающий: набор используется как фильтр, а не как список для загрузки.
     */
    public Set<String> usedAttachments() {
        Set<String> res = new HashSet<>();
        for (Element attachment : document.getElementsByTag("ri:attachment")) {
            addIfNotBlank(res, attachment.attr("ri:filename"));
        }
        // <img src> встречается в импортированном/вставленном HTML — файл берётся из папки вложений
        for (Element img : document.getElementsByTag("img")) {
            String fileName = fileNameOf(img.attr("src"));
            addIfNotBlank(res, fileName);
            // В src имя процентно-закодировано, а во вложениях Confluence лежит как есть: кладём оба
            // варианта, иначе «план%20схемы.png» не совпал бы с «план схемы.png» и файл бы не скачался.
            addIfNotBlank(res, urlDecoded(fileName));
        }
        for (Element macro : document.getElementsByTag("ac:structured-macro")) {
            String name = macro.attr("ac:name");
            if (!"drawio".equals(name) && !"inc-drawio".equals(name)) {
                continue;
            }
            String diagram = macroParameter(macro, "diagramName");
            if (StringUtils.isBlank(diagram)) {
                continue;
            }
            for (String candidate : DRAWIO_SUFFIXES) {
                res.add(diagram + candidate);
            }
        }
        return res;
    }

    /** Расширения, среди которых {@code DrawioRenderer} ищет исходник диаграммы (пустое — файл без расширения). */
    private static final String[] DRAWIO_SUFFIXES = {"", ".png", ".svg", ".drawio.png", ".drawio.svg"};

    /** Значение параметра макроса {@code <ac:parameter ac:name="…">} — как его читает {@code MacroTag}. */
    private static String macroParameter(Element macro, String name) {
        return macro.children().stream()
                .filter(e -> "ac:parameter".equals(e.nodeName()) && name.equals(e.attr("ac:name")))
                .map(Element::html)
                .findFirst()
                .orElse(null);
    }

    /** Имя файла из пути/URL картинки; {@code null}, если выделить не удалось. */
    private static String fileNameOf(String src) {
        if (StringUtils.isBlank(src)) {
            return null;
        }
        int slash = Math.max(src.lastIndexOf('/'), src.lastIndexOf('\\'));
        String name = slash >= 0 ? src.substring(slash + 1) : src;
        int query = name.indexOf('?');
        return query >= 0 ? name.substring(0, query) : name;
    }

    /** Процентно-декодированное имя; {@code null}, если декодировать нечего или строка битая. */
    private static String urlDecoded(String name) {
        if (name == null || name.indexOf('%') < 0) {
            return null;
        }
        try {
            // URLDecoder работает по правилам form-urlencoded, где «+» значит пробел. В имени файла
            // «+» — обычный символ («отчёт v1+2.png»), поэтому экранируем его перед декодированием.
            return URLDecoder.decode(name.replace("+", "%2B"), UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void addIfNotBlank(Set<String> target, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.add(value);
        }
    }

    /**
     * Карта {@code имя файла картинки → ссылка на скачивание} из rendered view. Confluence уже
     * отрезолвил в нём вложения любых страниц, поэтому это единственный источник URL для картинок,
     * лежащих не на текущей странице. Ссылки — как их отдаёт Confluence (обычно относительные от хоста).
     */
    public Map<String, String> imageSources() {
        Map<String, String> res = new HashMap<>();
        if (StringUtils.isBlank(view)) {
            return res;
        }
        for (Element img : viewDocument().getElementsByTag("img")) {
            String name = img.attr("data-linked-resource-default-alias");
            String src = img.attr("data-image-src");
            if (StringUtils.isBlank(src)) {
                src = img.attr("src");
            }
            if (StringUtils.isNoneBlank(name, src)) {
                res.putIfAbsent(name, src);
            }
        }
        return res;
    }

    @SneakyThrows
    public void convert(Map<MetadataKey, Object> metadata, Path attachment) {
        Path index = destination.resolve(format.indexFileName());
        String res = render(metadata, attachment, destination.resolve((String) metadata.get(MetadataKey.IMAGE)));

        if (printToStdout) {
            System.out.print(res);
        }
        if (StringUtils.countMatches(res, "\n") < 700) {
            Files.writeString(index, res);
        } else {
            SpliteratorAdoc.saveSplit(destination, format.indexFileName(), res, format.splitHeading(), metadata, format);
        }
    }

    /**
     * Конвертирует страницу в AsciiDoc и возвращает документ строкой, без записи файлов и без
     * splitting (для in-memory сценариев). Путь {@code attachment} используется как папка картинок.
     */
    public String toAdoc(Map<MetadataKey, Object> metadata, Path attachment) {
        return render(metadata, attachment, attachment);
    }

    /**
     * In-memory конвертация произвольного storage-фрагмента в AsciiDoc без резолва ссылок (ссылки —
     * локально/плейсхолдером). Для версий страницы и подобных случаев, где нет rendered view.
     */
    public String toAdoc(String title) {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        Map<MetadataKey, Object> metadata = new HashMap<>();
        metadata.put(MetadataKey.LINKS, Map.of());
        metadata.put(MetadataKey.TITLE, title == null ? "" : title);
        metadata.put(MetadataKey.IMAGE, "attache");
        metadata.put(MetadataKey.ATTACH_FOLDER, tmp);
        metadata.put(MetadataKey.ATTACH_FOLDER_NAME, "attache");
        metadata.put(MetadataKey.FILES_FOLDER, tmp);
        metadata.put(MetadataKey.FILES_FOLDER_NAME, "files");
        metadata.put(MetadataKey.DESTINATION_FOLDER, tmp);
        metadata.put(MetadataKey.COLOR, Boolean.FALSE);
        metadata.put(MetadataKey.IN_MEMORY, Boolean.TRUE);
        return toAdoc(metadata, tmp);
    }

    private String render(Map<MetadataKey, Object> metadata, Path attachment, Path imagesDir) {
        boolean isColor = (Boolean) metadata.getOrDefault(MetadataKey.COLOR, Boolean.FALSE);
        Elements bodyChildren = document.getElementsByTag("body").getFirst().children();
        List<Block> blocks = new AstBuilder(attachment, imagesDir).build(bodyChildren, metadata, isColor);
        String image = (String) metadata.get(MetadataKey.IMAGE);
        String body = format.writer(image).write(blocks);
        return format == OutputFormat.MD ? markdownHeader(metadata, body) : asciiDocHeader(metadata, image, body);
    }

    private static String asciiDocHeader(Map<MetadataKey, Object> metadata, String image, String body) {
        StringBuilder attrs = new StringBuilder();
        attr(attrs, "confluency-id", metadata.get(MetadataKey.PAGE_ID));
        attr(attrs, "confluency-version", value(metadata, MetadataKey.VERSION));
        attr(attrs, "confluency-url", value(metadata, MetadataKey.URL));
        attr(attrs, "confluency-space", value(metadata, MetadataKey.SPACE));
        attr(attrs, "confluency-author", value(metadata, MetadataKey.AUTHOR));
        attr(attrs, "confluency-created", value(metadata, MetadataKey.CREATED));
        List<String> labels = labels(metadata);
        String keywords = labels.isEmpty() ? "" : ":keywords: %s\n".formatted(String.join(", ", labels));
        return "= %s\n%s%s:toc: macro\n:imagesdir: ./%s\n\n%s\n".formatted(
                metadata.get(MetadataKey.TITLE), attrs, keywords, image, body);
    }

    private static void attr(StringBuilder sb, String name, Object value) {
        if (value != null && !value.toString().isBlank()) {
            sb.append(':').append(name).append(": ").append(value).append('\n');
        }
    }

    private static String markdownHeader(Map<MetadataKey, Object> metadata, String body) {
        StringBuilder front = new StringBuilder();
        front(front, "confluency-id", metadata.get(MetadataKey.PAGE_ID));
        front(front, "confluency-version", value(metadata, MetadataKey.VERSION));
        front(front, "confluency-url", value(metadata, MetadataKey.URL));
        front(front, "confluency-space", value(metadata, MetadataKey.SPACE));
        front(front, "confluency-author", value(metadata, MetadataKey.AUTHOR));
        front(front, "confluency-created", value(metadata, MetadataKey.CREATED));
        List<String> labels = labels(metadata);
        if (!labels.isEmpty()) {
            front.append("tags:\n");
            labels.forEach(l -> front.append("  - ").append(l).append('\n'));
        }
        String frontmatter = front.isEmpty() ? "" : "---\n" + front + "---\n\n";
        return "%s# %s\n\n%s\n".formatted(frontmatter, metadata.get(MetadataKey.TITLE), body);
    }

    private static void front(StringBuilder sb, String name, Object value) {
        if (value != null && !value.toString().isBlank()) {
            sb.append(name).append(": ").append(value).append('\n');
        }
    }

    private static String value(Map<MetadataKey, Object> metadata, MetadataKey key) {
        Object v = metadata.get(key);
        return v == null || v.toString().isBlank() ? null : v.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> labels(Map<MetadataKey, Object> metadata) {
        Object labels = metadata.get(MetadataKey.LABELS);
        return labels instanceof List<?> list ? (List<String>) list : List.of();
    }
}
