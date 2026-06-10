package ru.gitverse.adoct.parser.doc;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import ru.gitverse.adoct.LinksAttachment;
import ru.gitverse.adoct.LinksPage;
import ru.gitverse.adoct.LinksUser;
import ru.gitverse.adoct.LinksValue;
import ru.gitverse.adoct.MetadataKey;
import ru.gitverse.adoct.client.LinkResult;
import ru.gitverse.adoct.parser.PrintWriterReturn;

import java.util.List;
import java.util.Map;

public class ParseLink implements ParseTags {
    private PrintWriterReturn print;


    @Override
    public void setPrintWriter(PrintWriterReturn printWriter) {
        this.print = printWriter;
    }

    @Override
    public List<String> tags() {
        return List.of("ac:link");
    }

    @Override
    public void parse(Node node, ParseContext parseContext) {
        if (node instanceof Element e) {
            if (!print.isLastReturn()) {
                print.write(" ");
            }
            Map<LinksValue, LinkResult> links = getLinks(parseContext.getMetadata());
            Elements tagUser = e.getElementsByTag("ri:user");
            if (!tagUser.isEmpty()) {
                print.print(createLink(links, new LinksUser(tagUser.attr("ri:userkey"))));
                return;
            }
            Elements pageConfluence = e.getElementsByTag("ri:page");
            if (!pageConfluence.isEmpty()) {
                Elements plain = e.getElementsByTag("ac:plain-text-link-body");
                String title = plain.text();
                if (StringUtils.isBlank(title)) {
                    title = pageConfluence.attr("ri:content-title");
                }
                String anchor = e.attr("ac:anchor");
                if (StringUtils.isNotEmpty(anchor)) {
                    title = title + "#" + anchor;
                }

                LinksPage linksPage = new LinksPage(title,
                        pageConfluence.attr("ri:space-key"));
                print.print(createLink(links, linksPage));
                return;
            }
            Elements attachment = e.getElementsByTag("ri:attachment");
            if (!attachment.isEmpty()) {
                LinksAttachment linksAtachment = new LinksAttachment(attachment.attr("ri:filename"));
                print.print(createAttacheLink(links, linksAtachment, parseContext.getMetadata()));
                return;
            }
            Elements linkBody = e.getElementsByTag("ac:plain-text-link-body");
            if (!linkBody.isEmpty()) {
                print.print("<<%s, %s>> ".formatted(e.attr("ac:anchor"), linkBody.text()));
            }

        }

    }

    private String createAttacheLink(Map<LinksValue, LinkResult> links, LinksAttachment linksAtachment,
                                     Map<MetadataKey, Object> metadata) {
        LinkResult linkResult = links.get(linksAtachment);
        if (linkResult == null) {
            return "link:http://mock[%s]".formatted(linksAtachment);
            //throw new RuntimeException("Не найден резолвинг для %s".formatted(linkResult));
        }
        return "link:%s/%s[%s] ".formatted(metadata.get(MetadataKey.ATTACH_FOLDER_NAME), linkResult.title(),
                linkResult.title().replace("]", "\\]"));
    }

    private String createLink(Map<LinksValue, LinkResult> resolveLinks, LinksValue linksValue) {
        LinkResult linkResult = resolveLinks.get(linksValue);
        if (linkResult == null) {
            return "link:http://mock[%s] ".formatted(linksValue);
            //throw new RuntimeException("Не найден резолвинг для %s".formatted(linksValue));
        }
        return "link:%s[%s] ".formatted(linkResult.url(),
                linkResult.title().replace("]", "\\]"));
    }

    private static Map<LinksValue, LinkResult> getLinks(Map<MetadataKey, Object> metadata) {
        return (Map<LinksValue, LinkResult>) metadata.getOrDefault(MetadataKey.LINKS, Map.of());
    }

}
