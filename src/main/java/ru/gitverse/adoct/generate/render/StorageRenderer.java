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
import ru.gitverse.adoct.generate.asciidoc.AnchorIndex;
import ru.gitverse.adoct.generate.model.RenderResult;

import java.nio.file.Path;
import java.util.List;

/**
 * Обходит AST AsciiDoctor и формирует тело страницы в Confluence storage format (XHTML).
 *
 * <p>Поддерживаемое подмножество: заголовки, маркированные/нумерованные списки, списки определений,
 * простые таблицы (заголовок сверху), блоки PlantUML/кода, картинки и инлайн-форматирование.
 * Инлайн-фрагменты нормализует {@link InlineNormalizer}, примитивы формата строит {@link StorageFormat},
 * результат (XHTML + вложения) накапливает {@link RenderSink}.
 */
public final class StorageRenderer {

    private static final String SOURCE_STYLE = "source";
    private static final String PLANTUML_STYLE = "plantuml";

    private final String plantumlMacro;
    private final Path baseDir;
    private final String imagesDir;
    private final InlineNormalizer normalizer;

    /**
     * @param plantumlMacro имя серверного макроса PlantUML (зависит от установленного плагина)
     * @param baseDir       директория исходного {@code .adoc} — для разрешения путей картинок/файлов
     * @param imagesDir     значение атрибута {@code imagesdir} (может быть пустым)
     */
    public StorageRenderer(String plantumlMacro, Path baseDir, String imagesDir) {
        this(plantumlMacro, baseDir, imagesDir, AnchorIndex.empty(), null);
    }

    public StorageRenderer(String plantumlMacro, Path baseDir, String imagesDir,
                           AnchorIndex anchorIndex, Path currentFile) {
        this(plantumlMacro, baseDir, imagesDir, anchorIndex, currentFile, "");
    }

    /**
     * @param plantumlMacro имя серверного макроса PlantUML (зависит от установленного плагина)
     * @param baseDir       директория исходного {@code .adoc} — для разрешения путей картинок/файлов
     * @param imagesDir     значение атрибута {@code imagesdir} (может быть пустым)
     * @param anchorIndex   индекс якорей по всему набору публикуемых файлов (см. {@link AnchorIndex})
     * @param currentFile   путь текущего {@code .adoc} — чтобы отличить ссылку-якорь в пределах своей
     *                      страницы от ссылки на якорь, объявленный в другом файле
     * @param spaceKey      ключ пространства Confluence — для {@code ri:space-key} в межстраничных ссылках
     *                      (нужен новому редактору; пустой — атрибут не добавляется)
     */
    public StorageRenderer(String plantumlMacro, Path baseDir, String imagesDir,
                           AnchorIndex anchorIndex, Path currentFile, String spaceKey) {
        this.plantumlMacro = plantumlMacro;
        this.baseDir = baseDir;
        this.imagesDir = imagesDir == null ? "" : imagesDir;
        this.normalizer = new InlineNormalizer(baseDir, anchorIndex, currentFile, spaceKey);
    }

    public RenderResult render(Document document) {
        RenderSink sink = new RenderSink();
        renderBlocks(document.getBlocks(), sink);
        return new RenderResult(sink.xhtml(), sink.attachments());
    }

    private void renderBlocks(List<StructuralNode> blocks, RenderSink sink) {
        if (blocks == null) {
            return;
        }
        for (StructuralNode node : blocks) {
            renderNode(node, sink);
        }
    }

    private void renderNode(StructuralNode node, RenderSink sink) {
        switch (node.getContext()) {
            case "section" -> renderSection((Section) node, sink);
            case "paragraph" -> renderParagraph(node, sink);
            case "ulist" -> renderList(node, "ul", sink);
            case "olist" -> renderList(node, "ol", sink);
            case "colist" -> renderList(node, "ol", sink); // пояснения к callout'ам → нумерованный список
            case "example" -> renderExample(node, sink);
            case "quote" -> renderQuote(node, sink);
            case "verse" -> renderVerse(node, sink);
            case "sidebar" -> renderRichMacro("panel", node.getTitle(), node, sink);
            case "floating_title" -> renderFloatingTitle(node, sink);
            case "thematic_break" -> sink.append("<hr/>");
            case "dlist" -> renderDescriptionList((DescriptionList) node, sink);
            case "table" -> renderTable((Table) node, sink);
            case "admonition" -> renderAdmonition(node, sink);
            case "toc" -> sink.append(StorageFormat.tocMacro(tocLevels(node)));
            case "image" -> renderImage(node, sink);
            case "pass" -> sink.append(content(node)); // raw passthrough (напр. макрос include от IncludeProcessor)
            case "listing", "literal" -> renderListing(node, sink);
            default -> renderBlocks(node.getBlocks(), sink); // open/example/etc.: рекурсия
        }
    }

    private void renderSection(Section section, RenderSink sink) {
        int level = Math.min(Math.max(section.getLevel(), 1), 6);
        String style = alignmentStyle(section.getRole());
        sink.append(style == null ? "<h" + level + ">" : "<h" + level + " style=\"" + style + "\">");
        // Якорь секции — чтобы на неё можно было сослаться (<<id>>) с этой или другой страницы.
        String id = section.getId();
        if (id != null && !id.isBlank()) {
            sink.append(StorageFormat.anchorMacro(id));
        }
        // getTitle() уже отдаёт готовый инлайн-HTML (напр. `code` → <code>, *bold* → <strong>),
        // поэтому прогоняем через нормализатор, а НЕ экранируем — иначе теги заголовка видны как литерал.
        sink.append(inline(section.getTitle(), sink)).append("</h" + level + ">");
        renderBlocks(section.getBlocks(), sink);
    }

    private void renderParagraph(StructuralNode node, RenderSink sink) {
        String style = alignmentStyle(node.getRole());
        sink.append(style == null ? "<p>" : "<p style=\"" + style + "\">")
                .append(inline(content(node), sink))
                .append("</p>");
    }

    /** Роль выравнивания AsciiDoc → инлайн-стиль Confluence; {@code null}, если роль не про выравнивание. */
    private static String alignmentStyle(String role) {
        if (role == null) {
            return null;
        }
        return switch (role) {
            case "text-left" -> "text-align: left;";
            case "text-right" -> "text-align: right;";
            case "text-center" -> "text-align: center;";
            case "text-justify" -> "text-align: justify;";
            default -> null;
        };
    }

    private void renderList(StructuralNode node, String tag, RenderSink sink) {
        sink.append('<').append(tag).append('>');
        for (StructuralNode item : node.getBlocks()) {
            ListItem li = (ListItem) item;
            sink.append("<li>").append(itemText(li, sink));
            renderBlocks(li.getBlocks(), sink); // вложенные блоки (в т.ч. вложенные списки)
            sink.append("</li>");
        }
        sink.append("</").append(tag).append('>');
    }

    private void renderDescriptionList(DescriptionList node, RenderSink sink) {
        sink.append("<dl>");
        for (DescriptionListEntry entry : node.getItems()) {
            for (ListItem term : entry.getTerms()) {
                sink.append("<dt>").append(itemText(term, sink)).append("</dt>");
            }
            ListItem description = entry.getDescription();
            if (description != null) {
                sink.append("<dd>").append(itemText(description, sink));
                renderBlocks(description.getBlocks(), sink); // вложенные блоки (таблицы/списки через `+`)
                sink.append("</dd>");
            }
        }
        sink.append("</dl>");
    }

    private void renderTable(Table table, RenderSink sink) {
        String width = strAttr(table, "width");
        sink.append(width == null ? "<table>" : "<table style=\"width: " + width + ";\">");
        String title = table.getTitle();
        if (title != null && !title.isBlank()) {
            sink.append("<caption>").append(inline(title, sink)).append("</caption>");
        }
        renderRows(table.getHeader(), "thead", true, sink);
        renderRows(table.getBody(), "tbody", false, sink);
        renderRows(table.getFooter(), "tfoot", false, sink);
        sink.append("</table>");
    }

    private void renderRows(List<Row> rows, String sectionTag, boolean headerSection, RenderSink sink) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        sink.append("<").append(sectionTag).append(">");
        for (Row row : rows) {
            sink.append("<tr>");
            for (Cell cell : row.getCells()) {
                boolean th = headerSection || "header".equals(cell.getStyle());
                String tag = th ? "th" : "td";
                sink.append(cellOpenTag(tag, cell)).append(cellText(cell, sink)).append("</").append(tag).append(">");
            }
            sink.append("</tr>");
        }
        sink.append("</").append(sectionTag).append(">");
    }

    /** Открывающий тег ячейки с colspan/rowspan (если > 1). */
    private static String cellOpenTag(String tag, Cell cell) {
        StringBuilder sb = new StringBuilder("<").append(tag);
        if (cell.getColspan() > 1) {
            sb.append(" colspan=\"").append(cell.getColspan()).append("\"");
        }
        if (cell.getRowspan() > 1) {
            sb.append(" rowspan=\"").append(cell.getRowspan()).append("\"");
        }
        return sb.append(">").toString();
    }

    /**
     * Admonition ({@code [NOTE]/[TIP]/[CAUTION]/[WARNING]/[IMPORTANT]}) → панель Confluence
     * ({@code info/tip/note/warning} по {@link StorageFormat#admonitionMacroName}). Тело — вложенные
     * блоки (блочная форма {@code ====}) либо инлайн-контент (однострочная {@code NOTE: ...}).
     */
    private void renderAdmonition(StructuralNode node, RenderSink sink) {
        renderRichMacro(StorageFormat.admonitionMacroName(String.valueOf(node.getAttribute("name"))),
                node.getTitle(), node, sink);
    }

    /** Example-блок: {@code [%collapsible]} → макрос {@code expand}, иначе → {@code tip} (как в Confluence Publisher). */
    private void renderExample(StructuralNode node, RenderSink sink) {
        boolean collapsible = node.getAttribute("collapsible-option") != null;
        renderRichMacro(collapsible ? "expand" : "tip", node.getTitle(), node, sink);
    }

    /** Цитата → {@code <blockquote>} с телом и (опц.) атрибуцией. */
    private void renderQuote(StructuralNode node, RenderSink sink) {
        sink.append("<blockquote>");
        if (node.getBlocks() != null && !node.getBlocks().isEmpty()) {
            renderBlocks(node.getBlocks(), sink);
        } else {
            sink.append("<p>").append(inline(content(node), sink)).append("</p>");
        }
        appendAttribution(node, sink);
        sink.append("</blockquote>");
    }

    /** Verse ({@code [verse]}) → {@code <pre>} (сохраняет переносы строк) + (опц.) атрибуция. */
    private void renderVerse(StructuralNode node, RenderSink sink) {
        sink.append("<pre>").append(inline(content(node), sink)).append("</pre>");
        appendAttribution(node, sink);
    }

    /** Дискретный заголовок ({@code [discrete]}) → {@code <hN>} без секции/якоря. */
    private void renderFloatingTitle(StructuralNode node, RenderSink sink) {
        int level = Math.min(Math.max(node.getLevel(), 1), 6);
        sink.append("<h" + level + ">").append(inline(node.getTitle(), sink)).append("</h" + level + ">");
    }

    /** Атрибуция цитаты/verse: {@code attribution[, citetitle]} → {@code <p>— ...</p>}. */
    private void appendAttribution(StructuralNode node, RenderSink sink) {
        String attribution = strAttr(node, "attribution");
        String citation = strAttr(node, "citetitle");
        String credit = attribution == null ? citation
                : citation == null ? attribution : attribution + ", " + citation;
        if (credit != null && !credit.isBlank()) {
            sink.append("<p>— ").append(StorageFormat.escapeText(credit)).append("</p>");
        }
    }

    /** Структурный макрос с {@code rich-text-body} (admonition/example/sidebar): тело — вложенные блоки или инлайн. */
    private void renderRichMacro(String macroName, String title, StructuralNode node, RenderSink sink) {
        sink.append("<ac:structured-macro ac:name=\"").append(macroName).append("\">");
        if (title != null && !title.isBlank()) {
            sink.append("<ac:parameter ac:name=\"title\">")
                    .append(StorageFormat.escapeText(title))
                    .append("</ac:parameter>");
        }
        sink.append("<ac:rich-text-body>");
        if (node.getBlocks() != null && !node.getBlocks().isEmpty()) {
            renderBlocks(node.getBlocks(), sink);
        } else {
            sink.append("<p>").append(inline(content(node), sink)).append("</p>");
        }
        sink.append("</ac:rich-text-body></ac:structured-macro>");
    }

    private void renderImage(StructuralNode node, RenderSink sink) {
        String target = String.valueOf(node.getAttribute("target"));
        sink.attachments().add(resolveImage(target));
        String image = StorageFormat.image(
                Path.of(target).getFileName().toString(),
                strAttr(node, "alt"),
                node.getTitle(),
                strAttr(node, "width"),
                strAttr(node, "height"));
        String link = strAttr(node, "link");
        if (link != null && !link.isBlank()) {
            sink.append("<a href=\"").append(StorageFormat.escapeAttr(link)).append("\">")
                    .append(image).append("</a>");
        } else {
            sink.append(image);
        }
    }

    private void renderListing(StructuralNode node, RenderSink sink) {
        String source = node instanceof Block block && block.getSource() != null ? block.getSource() : "";
        String style = node.getStyle();
        if (PLANTUML_STYLE.equalsIgnoreCase(style)) {
            sink.append(StorageFormat.macro(plantumlMacro, source));
        } else if (SOURCE_STYLE.equalsIgnoreCase(style)) {
            sink.append(StorageFormat.codeMacro(
                    StorageFormat.confluenceLang(strAttr(node, "language")),
                    node.getTitle(),
                    node.getAttribute("linenums") != null,
                    strAttr(node, "start"),
                    strAttr(node, "collapse"),
                    source));
        } else {
            // литерал/листинг без [source] → noformat
            sink.append(StorageFormat.noformatMacro(node.getTitle(), source));
        }
    }

    private static String strAttr(StructuralNode node, String name) {
        Object value = node.getAttribute(name);
        return value == null ? null : value.toString();
    }

    /** Значение {@code :toclevels:} документа (по умолчанию 2). */
    private static int tocLevels(StructuralNode node) {
        Object value = node.getDocument().getAttribute("toclevels");
        if (value == null) {
            return 2;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return 2;
        }
    }

    private String cellText(Cell cell, RenderSink sink) {
        return inline(cell.getText(), sink);
    }

    /** Текст элемента списка; у элементов без текста (только вложенные блоки) {@code getText()} может вернуть null. */
    private String itemText(ListItem item, RenderSink sink) {
        return item.hasText() ? inline(item.getText(), sink) : "";
    }

    private String inline(String html, RenderSink sink) {
        return normalizer.normalize(html, sink.attachments());
    }

    private Path resolveImage(String target) {
        Path p = imagesDir.isEmpty() ? Path.of(target) : Path.of(imagesDir, target);
        return baseDir == null ? p : baseDir.resolve(p);
    }

    private static String content(StructuralNode node) {
        Object c = node.getContent();
        return c == null ? "" : c.toString();
    }
}
