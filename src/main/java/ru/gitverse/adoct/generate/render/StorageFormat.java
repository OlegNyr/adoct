package ru.gitverse.adoct.generate.render;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Примитивы Confluence storage format: экранирование текста/атрибутов, CDATA и сборка готовых
 * фрагментов (макрос, якорь, картинка, ссылки на страницу/вложение). Чистые функции без состояния.
 *
 * <p>Тело ссылок ({@code link-body}) подставляется как есть — это уже нормализованный инлайн-HTML;
 * экранируются только заголовки/имена/атрибуты.
 */
final class StorageFormat {

    private StorageFormat() {
    }

    /**
     * Имя Confluence-макроса панели для AsciiDoc-admonition. Имена НЕ совпадают с AsciiDoc:
     * {@code note→info}, {@code tip→tip}, {@code caution/warning→note}, {@code important→warning}
     * (как в Confluence Publisher). Неизвестное/пустое → {@code info}.
     */
    static String admonitionMacroName(String asciidocName) {
        if (asciidocName == null) {
            return "info";
        }
        return switch (asciidocName.toLowerCase(Locale.ROOT)) {
            case "tip" -> "tip";
            case "caution", "warning" -> "note";
            case "important" -> "warning";
            default -> "info"; // note + запасной вариант
        };
    }

    /** Экранирует текстовый узел: {@code & < >}. */
    static String escapeText(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /** Экранирует значение атрибута: как текст плюс {@code "}. */
    static String escapeAttr(String s) {
        return escapeText(s).replace("\"", "&quot;");
    }

    /** Оборачивает текст в CDATA, безопасно разбивая последовательность {@code ]]>}. */
    static String cdata(String text) {
        return "<![CDATA[" + text.replace("]]>", "]]]]><![CDATA[>") + "]]>";
    }

    /** Структурный макрос с телом в {@code plain-text-body} (PlantUML и т.п.). */
    static String macro(String name, String plainTextBody) {
        return "<ac:structured-macro ac:name=\"" + escapeAttr(name) + "\">"
                + "<ac:plain-text-body>" + cdata(plainTextBody) + "</ac:plain-text-body>"
                + "</ac:structured-macro>";
    }

    /** Поддерживаемые Confluence языки макроса {@code code} (из Confluence Publisher). */
    private static final Set<String> SUPPORTED_LANGS = Set.of(
            "actionscript3", "applescript", "bash", "c#", "cpp", "css", "coldfusion", "delphi", "diff",
            "erl", "groovy", "xml", "java", "jfx", "js", "matlab", "php", "perl", "text", "powershell",
            "py", "ruby", "rust", "sql", "sass", "scala", "toml", "vb", "yml");

    /** Маппинг имён языков AsciiDoctor/highlight.js → имена Confluence (из Confluence Publisher). */
    private static final Map<String, String> LANG_MAP = Map.ofEntries(
            Map.entry("actionscript", "actionscript3"), Map.entry("as", "actionscript3"),
            Map.entry("osascript", "applescript"),
            Map.entry("sh", "bash"), Map.entry("zsh", "bash"),
            Map.entry("csharp", "c#"), Map.entry("cs", "c#"),
            Map.entry("hpp", "cpp"), Map.entry("cc", "cpp"), Map.entry("hh", "cpp"), Map.entry("c++", "cpp"),
            Map.entry("h++", "cpp"), Map.entry("cxx", "cpp"), Map.entry("hxx", "cpp"),
            Map.entry("dpr", "delphi"), Map.entry("dfm", "delphi"), Map.entry("pas", "delphi"),
            Map.entry("pascal", "delphi"),
            Map.entry("patch", "diff"),
            Map.entry("erlang", "erl"),
            Map.entry("html", "xml"), Map.entry("xhtml", "xml"), Map.entry("rss", "xml"), Map.entry("atom", "xml"),
            Map.entry("xjb", "xml"), Map.entry("xsd", "xml"), Map.entry("xsl", "xml"), Map.entry("plist", "xml"),
            Map.entry("svg", "xml"),
            Map.entry("jsp", "java"),
            Map.entry("javascript", "js"), Map.entry("jsx", "js"), Map.entry("json", "js"),
            Map.entry("pl", "perl"), Map.entry("pm", "perl"),
            Map.entry("plaintext", "text"), Map.entry("txt", "text"),
            Map.entry("ps", "powershell"), Map.entry("ps1", "powershell"),
            Map.entry("python", "py"), Map.entry("gyp", "py"),
            Map.entry("rb", "ruby"), Map.entry("gemspec", "ruby"), Map.entry("podspec", "ruby"),
            Map.entry("thor", "ruby"), Map.entry("irb", "ruby"),
            Map.entry("rs", "rust"),
            Map.entry("vbnet", "vb"), Map.entry("vbscript", "vb"), Map.entry("vbs", "vb"),
            Map.entry("yaml", "yml"));

    /** Имя языка для макроса {@code code} или {@code null}, если язык не поддерживается Confluence. */
    static String confluenceLang(String asciidocLang) {
        if (asciidocLang == null || asciidocLang.isBlank()) {
            return null;
        }
        String lower = asciidocLang.toLowerCase(Locale.ROOT);
        String mapped = LANG_MAP.getOrDefault(lower, lower);
        return SUPPORTED_LANGS.contains(mapped) ? mapped : null;
    }

    /** Макрос {@code code} с опциональными параметрами; тело — CDATA. */
    static String codeMacro(String language, String title, boolean lineNumbers, String firstLine,
                            String collapse, String body) {
        StringBuilder sb = new StringBuilder("<ac:structured-macro ac:name=\"code\">");
        if (language != null) {
            sb.append(param("language", language));
        }
        if (title != null && !title.isBlank()) {
            sb.append(param("title", title));
        }
        if (lineNumbers) {
            sb.append(param("linenumbers", "true"));
        }
        if (firstLine != null && !firstLine.isBlank()) {
            sb.append(param("firstline", firstLine));
        }
        if (collapse != null && !collapse.isBlank()) {
            sb.append(param("collapse", collapse));
        }
        return sb.append("<ac:plain-text-body>").append(cdata(body))
                .append("</ac:plain-text-body></ac:structured-macro>").toString();
    }

    /** Макрос {@code noformat} (литералы/листинги без {@code [source]}); тело — CDATA. */
    static String noformatMacro(String title, String body) {
        String titleParam = title == null || title.isBlank() ? "" : param("title", title);
        return "<ac:structured-macro ac:name=\"noformat\">" + titleParam
                + "<ac:plain-text-body>" + cdata(body) + "</ac:plain-text-body></ac:structured-macro>";
    }

    private static String param(String name, String value) {
        return "<ac:parameter ac:name=\"" + escapeAttr(name) + "\">" + escapeText(value) + "</ac:parameter>";
    }

    /** Макрос оглавления Confluence с максимальным уровнем. */
    static String tocMacro(int maxLevel) {
        return "<ac:structured-macro ac:name=\"toc\"><ac:parameter ac:name=\"maxLevel\">"
                + maxLevel + "</ac:parameter></ac:structured-macro>";
    }

    /** Макрос-якорь Confluence (цель внутренней ссылки). */
    static String anchorMacro(String name) {
        return "<ac:structured-macro ac:name=\"anchor\"><ac:parameter ac:name=\"\">"
                + escapeText(name) + "</ac:parameter></ac:structured-macro>";
    }

    /** Блочная картинка по вложению страницы с опциональными атрибутами alt/title/width/height. */
    static String image(String fileName, String alt, String title, String width, String height) {
        StringBuilder sb = new StringBuilder("<ac:image");
        appendAttr(sb, "ac:alt", alt);
        appendAttr(sb, "ac:title", title);
        appendAttr(sb, "ac:width", width);
        appendAttr(sb, "ac:height", height);
        if (notBlank(width) || notBlank(height)) {
            sb.append(" ac:custom-width=\"true\"");
        }
        return sb.append("><ri:attachment ri:filename=\"").append(escapeAttr(fileName))
                .append("\"/></ac:image>").toString();
    }

    private static void appendAttr(StringBuilder sb, String name, String value) {
        if (notBlank(value)) {
            sb.append(' ').append(name).append("=\"").append(escapeAttr(value)).append('"');
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Инлайн-картинка по вложению страницы. */
    static String inlineImageAttachment(String fileName, String alt) {
        return "<ac:image ac:inline=\"true\"" + altAttr(alt)
                + "><ri:attachment ri:filename=\"" + escapeAttr(fileName) + "\"/></ac:image>";
    }

    /** Инлайн-картинка по внешнему URL. */
    static String inlineImageUrl(String url, String alt) {
        return "<ac:image ac:inline=\"true\"" + altAttr(alt)
                + "><ri:url ri:value=\"" + escapeAttr(url) + "\"/></ac:image>";
    }

    private static String altAttr(String alt) {
        return alt == null || alt.isBlank() ? "" : " ac:alt=\"" + escapeAttr(alt) + "\"";
    }

    /**
     * Ссылка на другую страницу по заголовку, опционально с якорем на ней и ключом пространства.
     * {@code ri:space-key} нужен новому редактору Confluence (баг CONFCLOUD-69902); добавляется только
     * если {@code spaceKey} непустой.
     */
    static String pageLink(String title, String anchor, String spaceKey, String body) {
        String anchorAttr = anchor == null || anchor.isBlank()
                ? ""
                : " ac:anchor=\"" + escapeAttr(anchor) + "\"";
        String spaceAttr = spaceKey == null || spaceKey.isBlank()
                ? ""
                : " ri:space-key=\"" + escapeAttr(spaceKey) + "\"";
        return "<ac:link" + anchorAttr + "><ri:page ri:content-title=\"" + escapeAttr(title) + "\"" + spaceAttr + "/>"
                + "<ac:link-body>" + body + "</ac:link-body></ac:link>";
    }

    /** Ссылка-якорь в пределах текущей страницы. */
    static String anchorLink(String anchor, String body) {
        return "<ac:link ac:anchor=\"" + escapeAttr(anchor) + "\">"
                + "<ac:link-body>" + body + "</ac:link-body></ac:link>";
    }

    /** Ссылка на вложение страницы. */
    static String attachmentLink(String fileName, String body) {
        return "<ac:link><ri:attachment ri:filename=\"" + escapeAttr(fileName) + "\"/>"
                + "<ac:link-body>" + body + "</ac:link-body></ac:link>";
    }
}
