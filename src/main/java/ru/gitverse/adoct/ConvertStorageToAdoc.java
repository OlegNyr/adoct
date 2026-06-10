package ru.gitverse.adoct;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.io.output.TeeWriter;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import ru.gitverse.adoct.parser.PrintWriterReturn;
import ru.gitverse.adoct.parser.doc.ParseCommentId;
import ru.gitverse.adoct.parser.doc.ParseContext;
import ru.gitverse.adoct.parser.doc.ParseDispatcher;
import ru.gitverse.adoct.parser.doc.ParseHeader;
import ru.gitverse.adoct.parser.doc.ParseImg;
import ru.gitverse.adoct.parser.doc.ParseImgAcc;
import ru.gitverse.adoct.parser.doc.ParseLayout;
import ru.gitverse.adoct.parser.doc.ParseLink;
import ru.gitverse.adoct.parser.doc.ParseList;
import ru.gitverse.adoct.parser.doc.ParseParagraph;
import ru.gitverse.adoct.parser.doc.ParsePlaceholder;
import ru.gitverse.adoct.parser.doc.ParseSection;
import ru.gitverse.adoct.parser.doc.ParseTable;
import ru.gitverse.adoct.parser.doc.ParseTagMacros;
import ru.gitverse.adoct.parser.doc.ParseTags;
import ru.gitverse.adoct.parser.doc.ParseTime;
import ru.gitverse.adoct.parser.macros.MacrosAnchor;
import ru.gitverse.adoct.parser.macros.MacrosCode;
import ru.gitverse.adoct.parser.macros.MacrosDrawio;
import ru.gitverse.adoct.parser.macros.MacrosExpand;
import ru.gitverse.adoct.parser.macros.MacrosIgnoreOk;
import ru.gitverse.adoct.parser.macros.MacrosJira;
import ru.gitverse.adoct.parser.macros.MacrosNote;
import ru.gitverse.adoct.parser.macros.MacrosNumber;
import ru.gitverse.adoct.parser.macros.MacrosPlantuml;
import ru.gitverse.adoct.parser.macros.MacrosStep;
import ru.gitverse.adoct.parser.macros.MacrosTabs;
import ru.gitverse.adoct.parser.macros.MacrosToc;
import ru.gitverse.adoct.parser.macros.ParseMacros;
import ru.gitverse.adoct.parser.macros.ParseMacrosDispatcher;

import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ConvertStorageToAdoc {

    private final String content;
    private final Document document;
    private final String view;
    private final Path destination;
    @Setter
    @Getter
    private List<PostProcesing> procesings = List.of();


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
                continue;
            }

        }
        return Set.copyOf(res);
    }


    @SneakyThrows
    public void convert(Map<MetadataKey, Object> metadata, Path attachment) {

        Document document = Jsoup.parse(content, UTF_8.name());
        Path endPointFileNameDestinationAdoc = destination.resolve("index.adoc");


        try (StringWriter writerOut = new StringWriter(content.length());
             TeeWriter writer = new TeeWriter(writerOut, new OutputStreamWriter(System.out, UTF_8));
             PrintWriterReturn print = new PrintWriterReturn(writer, true)) {

            Boolean isColor = (Boolean) metadata.getOrDefault(MetadataKey.COLOR, Boolean.FALSE);

            print.println("= %s".formatted(metadata.get(MetadataKey.TITLE)));
            print.println(":toc: macro");
            print.println(":imagesdir: ./%s".formatted(metadata.get(MetadataKey.IMAGE)));

            print.println();


            List<ParseMacros> parserMacros = List.of(
                    new MacrosIgnoreOk(),
                    new MacrosExpand(),
                    new MacrosJira(),
                    new MacrosNumber(),
                    new MacrosDrawio(),
                    new MacrosCode(),
                    new MacrosPlantuml(),
                    new MacrosNote(),
                    new MacrosAnchor(),
                    new MacrosStep(),
                    new MacrosToc(),
                    new MacrosTabs()
            );
            List<ParseTags> parseTagsList = List.of(

                    new ParseImg(attachment, destination.resolve((String) metadata.get(MetadataKey.IMAGE))),
                    new ParseParagraph(),
                    new ParseTable(),
                    new ParseLayout(),
                    new ParseSection(),
                    new ParseHeader(),
                    new ParseList(),
                    new ParseTime(),
                    new ParseLink(),
                    new ParseImgAcc(),
                    new ParsePlaceholder(),
                    new ParseCommentId(),
                    new ParseTagMacros(new ParseMacrosDispatcher(parserMacros))
            );

            ParseDispatcher dispatcher = new ParseDispatcher(print, parseTagsList);

            Elements body = document.getElementsByTag("body").getFirst().children();
            dispatcher.parse(body, ParseContext.builder().metadata(metadata)
                    .workColor(isColor).build());
            print.flush();
            String res = writerOut.toString();
            for (PostProcesing procesing : procesings) {
                res = procesing.execute(res);
            }
            if (StringUtils.countMatches(res, "\n") < 700) {
                Files.writeString(endPointFileNameDestinationAdoc, res);
            } else {
                SpliteratorAdoc.saveSplit(destination, "index.adoc", res, "==", metadata);
            }
        }

    }

}
