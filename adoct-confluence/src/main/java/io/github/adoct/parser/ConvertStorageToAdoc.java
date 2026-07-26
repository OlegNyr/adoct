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

    public Map<String, String> resolveLink() {
        Document viewDoc = Jsoup.parse(view, UTF_8.name());
        Elements elements = viewDoc.getElementsByTag("a");
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
