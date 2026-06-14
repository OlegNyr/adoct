package ru.gitverse.adoct.parser;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import ru.gitverse.adoct.parser.ast.AsciiDocWriter;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.build.AstBuilder;
import ru.gitverse.adoct.parser.model.LinksAttachment;
import ru.gitverse.adoct.parser.model.LinksPage;
import ru.gitverse.adoct.parser.model.LinksUser;
import ru.gitverse.adoct.parser.model.LinksValue;
import ru.gitverse.adoct.parser.model.MetadataKey;

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
 * Конвертер Confluence storage-HTML → AsciiDoc.
 * <p>
 * Парсит HTML через Jsoup, строит промежуточное дерево {@link Block} ({@link AstBuilder}) и
 * сериализует его через {@link AsciiDocWriter}. Дисциплину пустых строк и раскладку таблиц держит
 * writer, поэтому строковые пост-процессоры не нужны.
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

    public ConvertStorageToAdoc(String content, String view, Path destination) {
        this.content = content;
        this.document = Jsoup.parse(content, UTF_8.name());
        this.view = view;
        if (!Files.exists(destination) || !Files.isDirectory(destination)) {
            throw new IllegalArgumentException("Папка назначения не существует %s".formatted(destination));
        }
        this.destination = destination;
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
        return Set.copyOf(res);
    }

    @SneakyThrows
    public void convert(Map<MetadataKey, Object> metadata, Path attachment) {
        Path index = destination.resolve("index.adoc");
        boolean isColor = (Boolean) metadata.getOrDefault(MetadataKey.COLOR, Boolean.FALSE);

        Elements bodyChildren = document.getElementsByTag("body").getFirst().children();
        AstBuilder astBuilder = new AstBuilder(attachment,
                destination.resolve((String) metadata.get(MetadataKey.IMAGE)));
        List<Block> blocks = astBuilder.build(bodyChildren, metadata, isColor);
        String body = new AsciiDocWriter().write(blocks);

        String res = "= %s\n:toc: macro\n:imagesdir: ./%s\n\n%s\n".formatted(
                metadata.get(MetadataKey.TITLE), metadata.get(MetadataKey.IMAGE), body);

        if (printToStdout) {
            System.out.print(res);
        }
        if (StringUtils.countMatches(res, "\n") < 700) {
            Files.writeString(index, res);
        } else {
            SpliteratorAdoc.saveSplit(destination, "index.adoc", res, "==", metadata);
        }
    }
}
