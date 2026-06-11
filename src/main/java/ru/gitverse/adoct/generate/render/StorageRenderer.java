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
            case "paragraph" -> sink.append("<p>").append(inline(content(node), sink)).append("</p>");
            case "ulist" -> renderList(node, "ul", sink);
            case "olist" -> renderList(node, "ol", sink);
            case "dlist" -> renderDescriptionList((DescriptionList) node, sink);
            case "table" -> renderTable((Table) node, sink);
            case "admonition" -> renderAdmonition(node, sink);
            case "image" -> renderImage(node, sink);
            case "pass" -> sink.append(content(node)); // raw passthrough (напр. макрос include от IncludeProcessor)
            case "listing", "literal" -> renderListing(node, sink);
            default -> renderBlocks(node.getBlocks(), sink); // open/example/etc.: рекурсия
        }
    }

    private void renderSection(Section section, RenderSink sink) {
        int level = Math.min(Math.max(section.getLevel(), 1), 6);
        // getTitle() уже отдаёт готовый инлайн-HTML (напр. `code` → <code>, *bold* → <strong>),
        // поэтому прогоняем через нормализатор, а НЕ экранируем — иначе теги заголовка видны как литерал.
        sink.append("<h").append(level).append('>')
                .append(inline(section.getTitle(), sink))
                .append("</h").append(level).append('>');
        renderBlocks(section.getBlocks(), sink);
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
        sink.append("<table>");
        List<Row> header = table.getHeader();
        if (header != null && !header.isEmpty()) {
            sink.append("<thead>");
            for (Row row : header) {
                sink.append("<tr>");
                for (Cell cell : row.getCells()) {
                    sink.append("<th>").append(cellText(cell, sink)).append("</th>");
                }
                sink.append("</tr>");
            }
            sink.append("</thead>");
        }
        sink.append("<tbody>");
        for (Row row : table.getBody()) {
            sink.append("<tr>");
            for (Cell cell : row.getCells()) {
                sink.append("<td>").append(cellText(cell, sink)).append("</td>");
            }
            sink.append("</tr>");
        }
        sink.append("</tbody></table>");
    }

    /**
     * Admonition ({@code [NOTE]/[TIP]/[CAUTION]/[WARNING]/[IMPORTANT]}) → панель Confluence
     * ({@code info/tip/note/warning} по {@link StorageFormat#admonitionMacroName}). Тело — вложенные
     * блоки (блочная форма {@code ====}) либо инлайн-контент (однострочная {@code NOTE: ...}).
     */
    private void renderAdmonition(StructuralNode node, RenderSink sink) {
        String macro = StorageFormat.admonitionMacroName(String.valueOf(node.getAttribute("name")));
        sink.append("<ac:structured-macro ac:name=\"").append(macro).append("\">");
        String title = node.getTitle();
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
        sink.append(StorageFormat.image(Path.of(target).getFileName().toString()));
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
