package ru.gitverse.adoct.anonymize;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.CDataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Анонимизация Confluence storage-HTML (а также отрендеренного view-HTML).
 *
 * <p>Сохраняет всё, на что опирается парсер: имена тегов и макросов, имена параметров,
 * структуру таблиц/секций/раскладок, классы и стили (цвета). Подменяет только листовой
 * текст и идентифицирующие значения атрибутов/параметров. Параметры, влияющие на разбор
 * ({@code language}, {@code start-numbering-with}), сохраняются как есть.
 *
 * <p>Согласованность обеспечивает общий {@link Anonymizer} (один на экспорт).
 */
public class StorageHtmlAnonymizer {

    /** Имена параметров макроса, которые нельзя менять — парсер на них завязан. */
    private static final Set<String> KEEP_PARAMS = Set.of("language", "start-numbering-with");

    private final Anonymizer anon;

    public StorageHtmlAnonymizer(Anonymizer anon) {
        this.anon = anon;
    }

    /** Анонимизирует фрагмент storage/view HTML и возвращает обновлённый HTML. */
    public String anonymizeFragment(String html) {
        if (StringUtils.isBlank(html)) {
            return html;
        }
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings().prettyPrint(false);
        for (Element child : doc.body().children()) {
            process(child);
        }
        return doc.body().html();
    }

    private void process(Element el) {
        String tag = el.tagName();
        if ("ac:structured-macro".equals(tag)) {
            handleMacro(el);
            return;
        }
        rewriteAttributes(el, tag);
        for (Node child : el.childNodes()) {
            if (child instanceof CDataNode cdata) {
                cdata.text(anon.inlineText(cdata.getWholeText()));
            } else if (child instanceof TextNode text) {
                text.text(anon.inlineText(text.getWholeText()));
            } else if (child instanceof Element element) {
                process(element);
            }
        }
    }

    private void rewriteAttributes(Element el, String tag) {
        switch (tag) {
            case "ri:user" -> replaceAttr(el, "ri:userkey", anon::userKey);
            case "ri:page" -> {
                replaceAttr(el, "ri:content-title", anon::title);
                replaceAttr(el, "ri:space-key", anon::spaceKey);
            }
            case "ri:attachment" -> replaceAttr(el, "ri:filename", anon::fileName);
            case "ri:url" -> replaceAttr(el, "ri:value", anon::url);
            case "a" -> replaceAttr(el, "href", anon::url);
            case "img" -> replaceAttr(el, "src", this::fileNameInPath);
            case "time" -> replaceAttr(el, "datetime", anon::date);
            default -> {
                // ничего
            }
        }
        // Якорь у ссылки (ac:link ac:anchor="...") — общий для нескольких тегов.
        replaceAttr(el, "ac:anchor", anon::anchor);
    }

    private void handleMacro(Element macro) {
        String macroName = macro.attr("ac:name");
        for (Element child : macro.children()) {
            switch (child.tagName()) {
                case "ac:parameter" -> handleParameter(macroName, child);
                case "ac:plain-text-body" -> replaceTextChildren(child, anon::multilineText);
                default -> process(child); // ac:rich-text-body и прочее — обычный рекурсивный обход
            }
        }
    }

    private void handleParameter(String macroName, Element param) {
        String paramName = param.attr("ac:name");
        if (KEEP_PARAMS.contains(paramName)) {
            return;
        }
        String value = param.wholeText();
        String replaced;
        if ("jira".equals(macroName) && "key".equals(paramName)) {
            replaced = anon.jiraKey(value);
        } else if (("drawio".equals(macroName) || "inc-drawio".equals(macroName)) && "diagramName".equals(paramName)) {
            replaced = anon.baseName(value);
        } else if ("anchor".equals(macroName)) {
            replaced = anon.anchor(value);
        } else {
            replaced = anon.inlineText(value);
        }
        param.text(replaced);
    }

    private void replaceTextChildren(Element el, UnaryOperator<String> transform) {
        for (Node child : el.childNodes()) {
            if (child instanceof CDataNode cdata) {
                cdata.text(transform.apply(cdata.getWholeText()));
            } else if (child instanceof TextNode text) {
                text.text(transform.apply(text.getWholeText()));
            } else if (child instanceof Element element) {
                process(element);
            }
        }
    }

    private void replaceAttr(Element el, String attr, UnaryOperator<String> transform) {
        if (el.hasAttr(attr)) {
            el.attr(attr, transform.apply(el.attr(attr)));
        }
    }

    /** Подменяет имя файла в последнем сегменте пути (для img src), сохраняя структуру каталогов. */
    private String fileNameInPath(String src) {
        if (StringUtils.isBlank(src)) {
            return src;
        }
        int slash = src.lastIndexOf('/');
        if (slash < 0) {
            return anon.fileName(src);
        }
        return src.substring(0, slash + 1) + anon.fileName(src.substring(slash + 1));
    }
}
