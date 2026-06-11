package ru.gitverse.adoct.generate.render;

import org.asciidoctor.ast.Block;
import org.asciidoctor.ast.Cell;
import org.asciidoctor.ast.DescriptionList;
import org.asciidoctor.ast.DescriptionListEntry;
import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.ListItem;
import org.asciidoctor.ast.Row;
import org.asciidoctor.ast.Section;
import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.ast.Table;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Entities;
import ru.gitverse.adoct.generate.asciidoc.AdocPageTitle;
import ru.gitverse.adoct.generate.asciidoc.AnchorIndex;
import ru.gitverse.adoct.generate.model.RenderResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Обходит AST AsciiDoctor и формирует тело страницы в Confluence storage format (XHTML).
 *
 * <p>Поддерживаемое подмножество (этап 1): заголовки, маркированные/нумерованные списки,
 * простые таблицы (заголовок сверху), блоки PlantUML, картинки и примитивное инлайн-
 * форматирование (его готовый HTML отдаёт сам AsciiDoctor через {@code getContent()}/{@code getText()}).
 */
public final class StorageRenderer {

    private final String plantumlMacro;
    private final Path baseDir;
    private final String imagesDir;
    private final AnchorIndex anchorIndex;
    private final Path currentFile;

    /**
     * @param plantumlMacro имя серверного макроса PlantUML (зависит от установленного плагина)
     * @param baseDir       директория исходного {@code .adoc} — для разрешения путей картинок
     * @param imagesDir     значение атрибута {@code imagesdir} (может быть пустым)
     */
    public StorageRenderer(String plantumlMacro, Path baseDir, String imagesDir) {
        this(plantumlMacro, baseDir, imagesDir, AnchorIndex.empty(), null);
    }

    /**
     * @param plantumlMacro имя серверного макроса PlantUML (зависит от установленного плагина)
     * @param baseDir       директория исходного {@code .adoc} — для разрешения путей картинок
     * @param imagesDir     значение атрибута {@code imagesdir} (может быть пустым)
     * @param anchorIndex   индекс якорей по всему набору публикуемых файлов (см. {@link AnchorIndex})
     * @param currentFile   путь текущего {@code .adoc} — чтобы отличить ссылку-якорь в пределах своей
     *                      страницы от ссылки на якорь, объявленный в другом файле
     */
    public StorageRenderer(String plantumlMacro, Path baseDir, String imagesDir,
                           AnchorIndex anchorIndex, Path currentFile) {
        this.plantumlMacro = plantumlMacro;
        this.baseDir = baseDir;
        this.imagesDir = imagesDir == null ? "" : imagesDir;
        this.anchorIndex = anchorIndex == null ? AnchorIndex.empty() : anchorIndex;
        this.currentFile = currentFile == null ? null : currentFile.toAbsolutePath().normalize();
    }

    public RenderResult render(Document document) {
        StringBuilder out = new StringBuilder();
        List<Path> images = new ArrayList<>();
        renderBlocks(document.getBlocks(), out, images);
        return new RenderResult(out.toString(), images);
    }

    private void renderBlocks(List<StructuralNode> blocks, StringBuilder out, List<Path> images) {
        if (blocks == null) {
            return;
        }
        for (StructuralNode node : blocks) {
            renderNode(node, out, images);
        }
    }

    private void renderNode(StructuralNode node, StringBuilder out, List<Path> images) {
        String context = node.getContext();
        switch (context) {
            case "section" -> renderSection((Section) node, out, images);
            case "paragraph" -> out.append("<p>").append(inline(content(node), images)).append("</p>");
            case "ulist" -> renderList(node, "ul", out, images);
            case "olist" -> renderList(node, "ol", out, images);
            case "dlist" -> renderDescriptionList((DescriptionList) node, out, images);
            case "table" -> renderTable((Table) node, out, images);
            case "image" -> renderImage(node, out, images);
            case "pass" -> out.append(content(node)); // raw passthrough (напр. макрос include от IncludeProcessor)
            case "listing", "literal" -> renderListing(node, out);
            default -> renderBlocks(node.getBlocks(), out, images); // open/example/etc.: рекурсия
        }
    }

    private void renderSection(Section section, StringBuilder out, List<Path> images) {
        int level = Math.min(Math.max(section.getLevel(), 1), 6);
        // getTitle() уже отдаёт готовый инлайн-HTML (напр. `code` → <code>, *bold* → <strong>),
        // поэтому прогоняем через inline() (нормализация под storage format), а НЕ через escape()
        // — иначе теги заголовка экранируются и видны как литерал <code>…</code>.
        out.append("<h").append(level).append('>')
                .append(inline(section.getTitle(), images))
                .append("</h").append(level).append('>');
        renderBlocks(section.getBlocks(), out, images);
    }

    private void renderList(StructuralNode node, String tag, StringBuilder out, List<Path> images) {
        out.append('<').append(tag).append('>');
        for (StructuralNode item : node.getBlocks()) {
            ListItem li = (ListItem) item;
            out.append("<li>");
            out.append(itemText(li, images));
            // вложенные блоки (в т.ч. вложенные списки)
            renderBlocks(li.getBlocks(), out, images);
            out.append("</li>");
        }
        out.append("</").append(tag).append('>');
    }

    private void renderDescriptionList(DescriptionList node, StringBuilder out, List<Path> images) {
        out.append("<dl>");
        for (DescriptionListEntry entry : node.getItems()) {
            for (ListItem term : entry.getTerms()) {
                out.append("<dt>").append(itemText(term, images)).append("</dt>");
            }
            ListItem description = entry.getDescription();
            if (description != null) {
                out.append("<dd>");
                out.append(itemText(description, images));
                // вложенные блоки описания (таблицы, списки через продолжение `+`)
                renderBlocks(description.getBlocks(), out, images);
                out.append("</dd>");
            }
        }
        out.append("</dl>");
    }

    private void renderTable(Table table, StringBuilder out, List<Path> images) {
        out.append("<table>");
        List<Row> header = table.getHeader();
        if (header != null && !header.isEmpty()) {
            out.append("<thead>");
            for (Row row : header) {
                out.append("<tr>");
                for (Cell cell : row.getCells()) {
                    out.append("<th>").append(cellText(cell, images)).append("</th>");
                }
                out.append("</tr>");
            }
            out.append("</thead>");
        }
        out.append("<tbody>");
        for (Row row : table.getBody()) {
            out.append("<tr>");
            for (Cell cell : row.getCells()) {
                out.append("<td>").append(cellText(cell, images)).append("</td>");
            }
            out.append("</tr>");
        }
        out.append("</tbody></table>");
    }

    private void renderImage(StructuralNode node, StringBuilder out, List<Path> images) {
        String target = String.valueOf(node.getAttribute("target"));
        String fileName = Path.of(target).getFileName().toString();
        images.add(resolveImage(target));
        out.append("<ac:image><ri:attachment ri:filename=\"")
                .append(escapeAttr(fileName))
                .append("\"/></ac:image>");
    }

    private void renderListing(StructuralNode node, StringBuilder out) {
        String source = node instanceof Block block && block.getSource() != null ? block.getSource() : "";
        if ("plantuml".equalsIgnoreCase(node.getStyle())) {
            appendMacro(out, plantumlMacro, source);
        } else {
            appendMacro(out, "code", source);
        }
    }

    private void appendMacro(StringBuilder out, String macroName, String body) {
        out.append("<ac:structured-macro ac:name=\"").append(escapeAttr(macroName)).append("\">")
                .append("<ac:plain-text-body>").append(cdata(body)).append("</ac:plain-text-body>")
                .append("</ac:structured-macro>");
    }

    private Path resolveImage(String target) {
        Path p = imagesDir.isEmpty() ? Path.of(target) : Path.of(imagesDir, target);
        return baseDir == null ? p : baseDir.resolve(p);
    }

    private static String content(StructuralNode node) {
        Object c = node.getContent();
        return c == null ? "" : c.toString();
    }

    private String cellText(Cell cell, List<Path> images) {
        return inline(cell.getText(), images);
    }

    /** Текст элемента списка; у элементов без текста (только вложенные блоки) {@code getText()} может вернуть null. */
    private String itemText(ListItem item, List<Path> images) {
        return item.hasText() ? inline(item.getText(), images) : "";
    }

    /** Инлайн-якорь от {@code [[id]]}: AsciiDoctor отдаёт пустой {@code <a id="...">}. */
    private static final Pattern ANCHOR_DEF = Pattern.compile("<a id=\"([^\"]*)\"\\s*></a>");

    /** Внутренняя ссылка-перекрёстная ссылка от {@code <<id,текст>>}: {@code <a href="#id">текст</a>}. */
    private static final Pattern INTERNAL_LINK = Pattern.compile("<a href=\"#([^\"]+)\">(.*?)</a>");

    /** Межфайловая ссылка от {@code <<file.adoc#id,текст>>}: {@code <a href="file.adoc#id">текст</a>}. */
    private static final Pattern CROSS_DOC_LINK = Pattern.compile(
            "<a href=\"([^\"#]+\\.adoc)(?:#([^\"]+))?\">(.*?)</a>", Pattern.CASE_INSENSITIVE);

    /** Любая оставшаяся ссылка {@code <a href="X">текст</a>} (после .adoc и якорей) — внешняя или на локальный файл. */
    private static final Pattern FILE_LINK = Pattern.compile("<a href=\"([^\"]+)\">(.*?)</a>");

    /** URL со схемой ({@code http:}, {@code https:}, {@code mailto:} и т.п.) — такие ссылки не трогаем. */
    private static final Pattern URL_SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.\\-]*:");

    /**
     * Нормализует инлайн-HTML от AsciiDoctor под storage format Confluence. Применяется только к
     * инлайн-фрагментам (текст абзацев, элементов списков, ячеек, термины/описания {@code <dl>}) —
     * не к собранным макросам {@code <ac:...>} и CDATA. Делает:
     * <ol>
     *   <li>приводит фрагмент к строгому XML через jsoup: само-закрывает void-элементы
     *       ({@code <br>} → {@code <br/>}) и <b>чинит перекрытие тегов</b> (напр. сбойную разметку
     *       {@code <span><strong>..</span>..</strong>}, которую AsciiDoctor отдаёт при невалидном
     *       исходнике) — иначе Confluence отвергает страницу как невалидный XHTML;</li>
     *   <li>межфайловые ссылки {@code <<file.adoc#id,..>>} → {@code <ac:link><ri:page ...>} на
     *       страницу-цель по её заголовку (с якорем, если задан);</li>
     *   <li>якоря {@code [[id]]} ({@code <a id="id">}) → макрос {@code anchor} Confluence;</li>
     *   <li>внутренние ссылки {@code <<id,..>>} ({@code <a href="#id">}) → {@code <ac:link ac:anchor=..>}.
     *       Внешние ссылки ({@code href} без {@code #} и не {@code .adoc}) не трогаются.</li>
     * </ol>
     */
    String inline(String html, List<Path> attachments) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String s = wellFormed(html);
        s = replaceAll(CROSS_DOC_LINK, s, m -> crossDocLink(m.group(1), m.group(2), m.group(3)));
        s = replaceAll(ANCHOR_DEF, s, m -> anchorMacro(m.group(1)));
        s = replaceAll(INTERNAL_LINK, s, m -> internalLink(m.group(1), m.group(2)));
        s = replaceAll(FILE_LINK, s, m -> fileLink(m.group(1), m.group(2), m.group(), attachments));
        return s;
    }

    /**
     * Оставшаяся {@code <a href>} (после обработки {@code .adoc}-ссылок и якорей). Если это относительная
     * ссылка на существующий локальный файл — добавляем его во вложения и ссылаемся на attach Confluence;
     * иначе (внешний URL со схемой или {@code //host}) оставляем ссылку как есть.
     */
    private String fileLink(String href, String body, String original, List<Path> attachments) {
        if (URL_SCHEME.matcher(href).find() || href.startsWith("//")) {
            return original;
        }
        Path resolved = resolveDoc(href);
        if (!Files.isRegularFile(resolved)) {
            return original;
        }
        attachments.add(resolved);
        String fileName = resolved.getFileName().toString();
        String text = body == null || body.isBlank() ? fileName : body;
        return "<ac:link><ri:attachment ri:filename=\"" + escapeAttr(fileName) + "\"/>"
                + "<ac:link-body>" + text + "</ac:link-body></ac:link>";
    }

    /**
     * Чинит инлайн-HTML до строго валидного XHTML-фрагмента средствами jsoup: закрывает void-теги и
     * перебалансирует перекрывающиеся/незакрытые теги. Работает только над стандартными HTML-тегами
     * AsciiDoctor (на этом этапе ещё нет наших {@code <ac:...>}/CDATA).
     */
    private static String wellFormed(String html) {
        org.jsoup.nodes.Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings()
                .syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml)
                .prettyPrint(false)
                .escapeMode(Entities.EscapeMode.xhtml)
                .charset(StandardCharsets.UTF_8);
        return doc.body().html();
    }

    /** Межфайловая ссылка Confluence на другую страницу (по заголовку файла-цели), опц. с якорем. */
    private String crossDocLink(String path, String anchor, String body) {
        String title = AdocPageTitle.fromFileOrName(resolveDoc(path), path);
        String text = body == null || body.isBlank() ? title : body;
        String anchorAttr = anchor == null || anchor.isBlank() ? "" : " ac:anchor=\"" + escapeAttr(anchor) + "\"";
        return "<ac:link" + anchorAttr + "><ri:page ri:content-title=\"" + escapeAttr(title) + "\"/>"
                + "<ac:link-body>" + text + "</ac:link-body></ac:link>";
    }

    private Path resolveDoc(String path) {
        Path p = Path.of(path);
        return baseDir == null ? p : baseDir.resolve(p).normalize();
    }

    /** Макрос-якорь Confluence для цели внутренней ссылки. */
    private static String anchorMacro(String name) {
        return "<ac:structured-macro ac:name=\"anchor\"><ac:parameter ac:name=\"\">"
                + escape(name) + "</ac:parameter></ac:structured-macro>";
    }

    /**
     * Ссылка от {@code <a href="#id">}. Если якорь {@code id} объявлен в другом файле набора —
     * это ссылка на <b>другую страницу</b> Confluence (с якорем на ней); иначе — якорь в пределах
     * текущей страницы.
     */
    private String internalLink(String anchor, String body) {
        String text = body == null || body.isBlank() ? anchor : body;
        AnchorIndex.Target target = anchorIndex.lookup(anchor);
        if (target != null && !target.file().equals(currentFile)) {
            return "<ac:link ac:anchor=\"" + escapeAttr(anchor) + "\">"
                    + "<ri:page ri:content-title=\"" + escapeAttr(target.title()) + "\"/>"
                    + "<ac:link-body>" + text + "</ac:link-body></ac:link>";
        }
        return "<ac:link ac:anchor=\"" + escapeAttr(anchor) + "\">"
                + "<ac:link-body>" + text + "</ac:link-body></ac:link>";
    }

    /** {@link Matcher#replaceAll(java.util.function.Function)} с безопасным экранированием замены. */
    private static String replaceAll(Pattern pattern, String input,
                                     java.util.function.Function<Matcher, String> replacer) {
        Matcher m = pattern.matcher(input);
        StringBuilder sb = new StringBuilder(input.length() + 16);
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(replacer.apply(m)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Оборачивает текст в CDATA, безопасно разбивая последовательность {@code ]]>}. */
    private static String cdata(String text) {
        return "<![CDATA[" + text.replace("]]>", "]]]]><![CDATA[>") + "]]>";
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String escapeAttr(String s) {
        return escape(s).replace("\"", "&quot;");
    }
}
