package ru.gitverse.adoct.parser.build;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import ru.gitverse.adoct.parser.model.LinksAttachment;
import ru.gitverse.adoct.parser.model.LinksPage;
import ru.gitverse.adoct.parser.model.LinksUser;
import ru.gitverse.adoct.parser.model.LinksValue;
import ru.gitverse.adoct.parser.model.MetadataKey;
import ru.gitverse.adoct.parser.confluence.LinkResult;

import java.util.Map;

/**
 * Рендер {@code <ac:link>} в инлайн-AsciiDoc по резолву из {@code metadata[LINKS]}.
 * Логика перенесена из старого {@code ParseLink}; результат — без хвостовых пробелов
 * (расстановку пробелов берёт на себя writer).
 */
public final class LinkRenderer {

    private LinkRenderer() {
    }

    /**
     * Рендер упоминания пользователя (макрос {@code profile}, {@code ri:user}) по резолву из
     * {@code metadata[LINKS]}. Пустой ключ → пустая строка (аноним/удалённый пользователь).
     */
    public static String user(String userKey, Map<MetadataKey, Object> metadata) {
        if (StringUtils.isBlank(userKey)) {
            return "";
        }
        return link(links(metadata), new LinksUser(userKey));
    }

    public static String render(Element e, Map<MetadataKey, Object> metadata) {
        Map<LinksValue, LinkResult> links = links(metadata);

        Elements tagUser = e.getElementsByTag("ri:user");
        if (!tagUser.isEmpty()) {
            return link(links, new LinksUser(tagUser.attr("ri:userkey")));
        }
        Elements page = e.getElementsByTag("ri:page");
        if (!page.isEmpty()) {
            Elements plain = e.getElementsByTag("ac:plain-text-link-body");
            String title = plain.text();
            if (StringUtils.isBlank(title)) {
                title = page.attr("ri:content-title");
            }
            String anchor = e.attr("ac:anchor");
            if (StringUtils.isNotEmpty(anchor)) {
                title = title + "#" + anchor;
            }
            return link(links, new LinksPage(title, page.attr("ri:space-key")));
        }
        Elements attachment = e.getElementsByTag("ri:attachment");
        if (!attachment.isEmpty()) {
            return attachLink(links, new LinksAttachment(attachment.attr("ri:filename")), metadata);
        }
        Elements linkBody = e.getElementsByTag("ac:plain-text-link-body");
        if (!linkBody.isEmpty()) {
            return "<<%s, %s>>".formatted(e.attr("ac:anchor"), linkBody.text());
        }
        return "";
    }

    private static String link(Map<LinksValue, LinkResult> links, LinksValue key) {
        LinkResult res = links.get(key);
        if (res == null) {
            return "link:http://mock[%s]".formatted(key);
        }
        return "link:%s[%s]".formatted(res.url(), res.title().replace("]", "\\]"));
    }

    private static String attachLink(Map<LinksValue, LinkResult> links, LinksAttachment key,
                                     Map<MetadataKey, Object> metadata) {
        LinkResult res = links.get(key);
        if (res == null) {
            return "link:http://mock[%s]".formatted(key);
        }
        return "link:%s/%s[%s]".formatted(metadata.get(MetadataKey.ATTACH_FOLDER_NAME), res.title(),
                res.title().replace("]", "\\]"));
    }

    @SuppressWarnings("unchecked")
    private static Map<LinksValue, LinkResult> links(Map<MetadataKey, Object> metadata) {
        return (Map<LinksValue, LinkResult>) metadata.getOrDefault(MetadataKey.LINKS, Map.of());
    }
}
