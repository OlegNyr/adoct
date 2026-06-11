package ru.gitverse.adoct.generate.render;

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

    /** Структурный макрос с телом в {@code plain-text-body} (код, PlantUML). */
    static String macro(String name, String plainTextBody) {
        return "<ac:structured-macro ac:name=\"" + escapeAttr(name) + "\">"
                + "<ac:plain-text-body>" + cdata(plainTextBody) + "</ac:plain-text-body>"
                + "</ac:structured-macro>";
    }

    /** Макрос-якорь Confluence (цель внутренней ссылки). */
    static String anchorMacro(String name) {
        return "<ac:structured-macro ac:name=\"anchor\"><ac:parameter ac:name=\"\">"
                + escapeText(name) + "</ac:parameter></ac:structured-macro>";
    }

    /** Картинка по вложению страницы. */
    static String image(String fileName) {
        return "<ac:image><ri:attachment ri:filename=\"" + escapeAttr(fileName) + "\"/></ac:image>";
    }

    /** Ссылка на другую страницу по заголовку, опционально с якорем на ней. */
    static String pageLink(String title, String anchor, String body) {
        String anchorAttr = anchor == null || anchor.isBlank()
                ? ""
                : " ac:anchor=\"" + escapeAttr(anchor) + "\"";
        return "<ac:link" + anchorAttr + "><ri:page ri:content-title=\"" + escapeAttr(title) + "\"/>"
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
