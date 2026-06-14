package ru.gitverse.adoct.parser.build;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import ru.gitverse.adoct.parser.color.ColorParser;
import ru.gitverse.adoct.parser.ast.Inline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Строит список {@link Inline} из дочерних узлов элемента (абзаца, ячейки, заголовка, элемента списка).
 * Замена инлайн-части старого {@code ParseParagraph} + инлайн-натуры тегов {@code ac:link}/{@code img}/
 * {@code ac:image}/{@code time}/jira — без печати и без состояния writer'а.
 */
public final class InlineBuilder {

    private final ImageRenderer imageRenderer;

    public InlineBuilder(ImageRenderer imageRenderer) {
        this.imageRenderer = imageRenderer;
    }

    /** Строит инлайн-содержимое детей узла и обрезает пробелы по краям (защита от literal-абзацев). */
    public List<Inline> build(Node parent, BuildContext ctx) {
        return buildNodes(parent.childNodes(), ctx);
    }

    /** Строит инлайн из явного списка узлов (для ячеек/элементов списка, где блоки отделены отдельно). */
    public List<Inline> buildNodes(List<? extends Node> nodes, BuildContext ctx) {
        List<Inline> out = new ArrayList<>();
        for (Node node : nodes) {
            add(out, node, ctx);
        }
        return trimEdges(out);
    }

    /** Строит инлайн для одного узла (например, блок-уровневая ссылка/картинка). */
    public List<Inline> buildOne(Node node, BuildContext ctx) {
        return buildNodes(List.of(node), ctx);
    }

    private List<Inline> children(Node parent, BuildContext ctx) {
        List<Inline> out = new ArrayList<>();
        for (Node node : parent.childNodes()) {
            add(out, node, ctx);
        }
        return out;
    }

    private void add(List<Inline> out, Node node, BuildContext ctx) {
        String name = node.nodeName();
        switch (name.toLowerCase()) {
            case "#text" -> {
                String text = escape(node);
                if (!text.isEmpty()) {
                    out.add(new Inline.Text(text));
                }
            }
            case "span" -> {
                Optional<String> color = ctx.workColor() ? findColor(node.attr("style")) : Optional.empty();
                if (color.isPresent()) {
                    out.add(new Inline.Colored(color.get(), children(node, ctx)));
                } else {
                    out.addAll(children(node, ctx));
                }
            }
            case "i", "em" -> out.add(new Inline.Italic(children(node, ctx)));
            case "u" -> out.add(new Inline.Underline(children(node, ctx)));
            case "strong", "b" -> out.add(new Inline.Bold(children(node, ctx)));
            case "a" -> out.add(new Inline.Link(node.attr("href"), children(node, ctx)));
            case "br" -> out.add(new Inline.LineBreak());
            case "ac:link" -> raw(out, LinkRenderer.render((Element) node, ctx.metadata()));
            case "ac:image" -> raw(out, "image:" + ImageRenderer.acImage((Element) node, ((Element) node).text().trim()));
            case "img" -> raw(out, "image:" + imageRenderer.img((Element) node, ((Element) node).text().trim()));
            case "time" -> {
                String dt = node.attr("datetime");
                if (!dt.isEmpty()) {
                    out.add(new Inline.Text(dt));
                }
            }
            case "ac:inline-comment-marker" -> out.addAll(children(node, ctx));
            case "ac:placeholder" -> { /* выкидываем */ }
            case "ac:structured-macro" -> {
                if ("jira".equals(((Element) node).attr("ac:name"))) {
                    String key = macroParams((Element) node).get("key");
                    raw(out, "link:https://jira.example.com/browse/%s[]".formatted(key));
                }
            }
            default -> out.addAll(children(node, ctx));
        }
    }

    private static void raw(List<Inline> out, String adoc) {
        if (StringUtils.isNotEmpty(adoc)) {
            out.add(new Inline.Raw(adoc));
        }
    }

    /** Текст узла с экранированием '|' (чтобы не ломать разметку таблиц). */
    private static String escape(Node node) {
        String text = node instanceof TextNode t ? t.text() : node.toString();
        return text.replace("|", "\\|");
    }

    private static Optional<String> findColor(String style) {
        return Arrays.stream(StringUtils.split(style, ";"))
                .filter(it -> it.startsWith("color:"))
                .flatMap(it -> ColorParser.parseColor(it).stream())
                .findFirst();
    }

    private static Map<String, String> macroParams(Element element) {
        return element.children().stream()
                .filter(e -> e.nodeName().equals("ac:parameter"))
                .collect(Collectors.toMap(e -> e.attr("ac:name"), Element::html, (a, b) -> a));
    }

    // --- обрезка пробелов по краям инлайна --------------------------------

    private static List<Inline> trimEdges(List<Inline> nodes) {
        if (nodes.isEmpty()) {
            return nodes;
        }
        if (nodes.getFirst() instanceof Inline.Text t) {
            String s = StringUtils.stripStart(t.value(), null);
            nodes.set(0, new Inline.Text(s));
        }
        int last = nodes.size() - 1;
        if (nodes.get(last) instanceof Inline.Text t) {
            String s = StringUtils.stripEnd(t.value(), null);
            nodes.set(last, new Inline.Text(s));
        }
        nodes.removeIf(n -> n instanceof Inline.Text t && t.value().isEmpty());
        return nodes;
    }
}
