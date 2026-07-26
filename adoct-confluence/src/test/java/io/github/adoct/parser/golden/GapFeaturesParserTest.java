package io.github.adoct.parser.golden;

import org.junit.Test;
import io.github.adoct.parser.OutputFormat;
import io.github.adoct.parser.model.MetadataKey;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Гэп-фичи (форматонезависимые), сверяемые сразу в двух форматах: списки-задачи, эмотиконы, метки
 * страницы, подписи к картинкам, а также табличные фолбэки Markdown (admonition-в-ячейке, экранирование
 * {@code |}, вложенные таблицы). Один вход — проверки в {@code .adoc} и {@code .md}.
 */
public class GapFeaturesParserTest extends AbstractConvertParserTest {

    private String md(String storage) throws IOException {
        return convert(storage, Map.of(), OutputFormat.MD);
    }

    private String md(String storage, Map<MetadataKey, Object> extra) throws IOException {
        return convert(storage, extra, OutputFormat.MD);
    }

    // --- списки задач ------------------------------------------------------

    private static final String TASK_LIST =
            "<ac:task-list>"
            + "<ac:task><ac:task-status>complete</ac:task-status><ac:task-body>сделано</ac:task-body></ac:task>"
            + "<ac:task><ac:task-status>incomplete</ac:task-status><ac:task-body>в работе</ac:task-body></ac:task>"
            + "</ac:task-list>";

    @Test
    public void taskListAsciiDoc() throws IOException {
        String out = convert(TASK_LIST);
        assertTrue(out.contains("* [x] сделано"));
        assertTrue(out.contains("* [ ] в работе"));
    }

    @Test
    public void taskListMarkdown() throws IOException {
        String out = md(TASK_LIST);
        assertTrue(out.contains("- [x] сделано"));
        assertTrue(out.contains("- [ ] в работе"));
    }

    // --- эмотиконы ---------------------------------------------------------

    @Test
    public void emoticonTickAndCross() throws IOException {
        String storage = "<p><ac:emoticon ac:name=\"tick\"/> и <ac:emoticon ac:name=\"cross\"/></p>";
        assertTrue(convert(storage).contains("✅ и ❌"));
        assertTrue(md(storage).contains("✅ и ❌"));
    }

    @Test
    public void emoticonEmojiFallbackWins() throws IOException {
        String storage = "<p><ac:emoticon ac:name=\"smile\" ac:emoji-id=\"1f604\" ac:emoji-fallback=\"😄\"/></p>";
        assertTrue(convert(storage).contains("😄"));
        assertTrue(md(storage).contains("😄"));
    }

    // --- метки страницы ----------------------------------------------------

    @Test
    public void labelsBecomeKeywordsAndTags() throws IOException {
        Map<MetadataKey, Object> labels = Map.of(MetadataKey.LABELS, List.of("api", "docs"));
        assertTrue(convert("<p>текст</p>", labels).contains(":keywords: api, docs"));
        String out = md("<p>текст</p>", labels);
        assertTrue(out.contains("tags:"));
        assertTrue(out.contains("  - api"));
        assertTrue(out.contains("  - docs"));
    }

    // --- метаданные страницы в заголовке/frontmatter -----------------------

    @Test
    public void pageMetadataInAsciiDocHeaderAndMarkdownFrontmatter() throws IOException {
        Map<MetadataKey, Object> meta = Map.of(
                MetadataKey.PAGE_ID, "123",
                MetadataKey.URL, "https://wiki/x",
                MetadataKey.SPACE, "DOC",
                MetadataKey.AUTHOR, "Иван Иванов",
                MetadataKey.CREATED, "2024-01-01T00:00:00.000+0000");
        String adoc = convert("<p>текст</p>", meta);
        assertTrue(adoc.contains(":confluency-url: https://wiki/x"));
        assertTrue(adoc.contains(":confluency-space: DOC"));
        assertTrue(adoc.contains(":confluency-author: Иван Иванов"));
        assertTrue(adoc.contains(":confluency-created: 2024-01-01T00:00:00.000+0000"));

        String out = md("<p>текст</p>", meta);
        assertTrue(out.contains("---"));
        assertTrue(out.contains("confluency-space: DOC"));
        assertTrue(out.contains("confluency-author: Иван Иванов"));
        assertTrue(out.contains("confluency-url: https://wiki/x"));
    }

    // --- подпись к картинке ------------------------------------------------

    @Test
    public void imageCaption() throws IOException {
        String storage = "<ac:image><ri:attachment ri:filename=\"d.png\"/>"
                + "<ac:caption>Рисунок 1</ac:caption></ac:image>";
        String adoc = convert(storage);
        assertTrue(adoc.contains(".Рисунок 1"));
        assertTrue(adoc.contains("image::d.png["));
        assertTrue(md(storage).contains("Рисунок 1"));
    }

    // --- Page Properties (details) -----------------------------------------

    @Test
    public void detailsRendersPropertiesTableInBothFormats() throws IOException {
        String storage = "<ac:structured-macro ac:name=\"details\"><ac:rich-text-body>"
                + "<table><tbody>"
                + "<tr><th>Статус</th><td>Активен</td></tr>"
                + "<tr><th>Владелец</th><td>Иван</td></tr>"
                + "</tbody></table></ac:rich-text-body></ac:structured-macro>";
        String adoc = convert(storage);
        // тело Page Properties (таблица ключ/значение) не теряется и не превращается в под-заголовок
        assertTrue(adoc.contains("|==="));
        assertTrue(adoc.contains("Активен"));
        assertFalse(adoc.contains("== "));
        String out = md(storage);
        assertTrue(out.contains("Статус"));
        assertTrue(out.contains("Активен"));
    }

    // --- табличные фолбэки Markdown ---------------------------------------

    @Test
    public void admonitionInCellUsesEmojiInMarkdown() throws IOException {
        String storage = "<table><tbody><tr><td>"
                + "<ac:structured-macro ac:name=\"warning\"><ac:rich-text-body><p>осторожно</p>"
                + "</ac:rich-text-body></ac:structured-macro></td></tr></tbody></table>";
        String out = md(storage);
        assertTrue(out.contains("<table>"));
        assertTrue(out.contains("⚠️ осторожно"));
        assertFalse(out.contains("> [!"));
    }

    @Test
    public void pipeInCellEscapedInMarkdown() throws IOException {
        String storage = "<table><tbody>"
                + "<tr><th>H</th></tr><tr><td>a | b</td></tr></tbody></table>";
        assertTrue(md(storage).contains("a \\| b"));
    }

    @Test
    public void listInCellBecomesHtmlListInMarkdown() throws IOException {
        String storage = "<table><tbody><tr><td>"
                + "<ul><li>один</li><li>два</li></ul>"
                + "</td></tr></tbody></table>";
        String out = md(storage);
        assertTrue(out.contains("<table>"));
        assertTrue(out.contains("<ul><li>один</li><li>два</li></ul>"));
    }

    @Test
    public void plantumlMacroFencesInMarkdownAndBlockInAsciiDoc() throws IOException {
        String storage = "<ac:structured-macro ac:name=\"plantuml\">"
                + "<ac:plain-text-body>@startuml\nA -> B\n@enduml</ac:plain-text-body></ac:structured-macro>";
        assertTrue(convert(storage).contains("[plantuml, format=\"png\"]"));
        String out = md(storage);
        assertTrue(out.contains("```plantuml"));
        assertTrue(out.contains("A -> B"));
    }

    @Test
    public void nestedTableKeepsInnerHtmlInMarkdown() throws IOException {
        String storage = "<table><tbody><tr><td>"
                + "<table><tbody><tr><td>внутр</td></tr></tbody></table>"
                + "</td></tr></tbody></table>";
        String out = md(storage);
        // внешняя таблица в HTML-фолбэке, внутренняя сохранена как вложенный <table>
        assertTrue(out.indexOf("<table>") != out.lastIndexOf("<table>"));
        assertTrue(out.contains("внутр"));
    }
}
