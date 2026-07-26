package io.github.adoct.parser.build;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import io.github.adoct.parser.ast.Inline;
import io.github.adoct.parser.model.LinksAttachment;
import io.github.adoct.parser.model.LinksPage;
import io.github.adoct.parser.model.LinksUser;
import io.github.adoct.parser.model.LinksValue;
import io.github.adoct.parser.model.MetadataKey;
import io.github.adoct.parser.confluence.LinkResult;

import java.util.List;
import java.util.Map;

/**
 * Рендер {@code <ac:link>} в формат-нейтральные инлайн-узлы ({@link Inline.Link}/{@link Inline.CrossRef})
 * по резолву из {@code metadata[LINKS]}. Экранирование подписи — задача writer'а под конкретный формат.
 */
public final class LinkRenderer {

    private LinkRenderer() {
    }

    /**
     * Рендер упоминания пользователя (макрос {@code profile}, {@code ri:user}) по резолву из
     * {@code metadata[LINKS]}. Пустой ключ → пустой список (аноним/удалённый пользователь).
     */
    public static List<Inline> user(String userKey, Map<MetadataKey, Object> metadata) {
        if (StringUtils.isBlank(userKey)) {
            return List.of();
        }
        return List.of(link(links(metadata), new LinksUser(userKey)));
    }

    public static List<Inline> render(Element e, Map<MetadataKey, Object> metadata) {
        Map<LinksValue, LinkResult> links = links(metadata);

        Elements tagUser = e.getElementsByTag("ri:user");
        if (!tagUser.isEmpty()) {
            return List.of(link(links, new LinksUser(tagUser.attr("ri:userkey"))));
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
            return List.of(link(links, new LinksPage(title, page.attr("ri:space-key"))));
        }
        Elements attachment = e.getElementsByTag("ri:attachment");
        if (!attachment.isEmpty()) {
            return List.of(attachLink(links, new LinksAttachment(attachment.attr("ri:filename")), metadata));
        }
        Elements linkBody = e.getElementsByTag("ac:plain-text-link-body");
        if (!linkBody.isEmpty()) {
            return List.of(new Inline.CrossRef(e.attr("ac:anchor"), linkBody.text()));
        }
        return List.of();
    }

    private static Inline link(Map<LinksValue, LinkResult> links, LinksValue key) {
        LinkResult res = links.get(key);
        if (res == null) {
            return new Inline.Link("http://mock", List.of(new Inline.Text(key.toString())));
        }
        return new Inline.Link(res.url(), List.of(new Inline.Text(res.title())));
    }

    private static Inline attachLink(Map<LinksValue, LinkResult> links, LinksAttachment key,
                                     Map<MetadataKey, Object> metadata) {
        LinkResult res = links.get(key);
        if (res == null) {
            return new Inline.Link("http://mock", List.of(new Inline.Text(key.toString())));
        }
        String url = "%s/%s".formatted(metadata.get(MetadataKey.ATTACH_FOLDER_NAME), res.title());
        return new Inline.Link(url, List.of(new Inline.Text(res.title())));
    }

    @SuppressWarnings("unchecked")
    private static Map<LinksValue, LinkResult> links(Map<MetadataKey, Object> metadata) {
        return (Map<LinksValue, LinkResult>) metadata.getOrDefault(MetadataKey.LINKS, Map.of());
    }
}
