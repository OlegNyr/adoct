package ru.gitverse.adoct.generate.asciidoc;

import org.asciidoctor.ast.Document;
import org.asciidoctor.extension.IncludeProcessor;
import org.asciidoctor.extension.PreprocessorReader;

import java.nio.file.Path;
import java.util.Map;

/**
 * Перехватывает директивы {@code include::*.adoc[]} и вместо инлайнинга содержимого вставляет
 * макрос Confluence «Include Page» (вставка другой страницы). Целевая страница адресуется по
 * заголовку — первому заголовку включаемого файла ({@code = Title}, иначе первый раздел, иначе
 * имя файла), что совпадает с тем, как {@code publish-dir} именует страницы.
 *
 * <p>Включения не-{@code .adoc} (например, фрагменты кода) обрабатываются стандартно — инлайнятся.
 */
public final class ConfluenceIncludeProcessor extends IncludeProcessor {

    @Override
    public boolean handles(String target) {
        return target != null && target.toLowerCase().endsWith(".adoc");
    }

    @Override
    public void process(Document document, PreprocessorReader reader, String target, Map<String, Object> attributes) {
        Path includeFile = resolve(document, target);
        String title = AdocPageTitle.fromFileOrName(includeFile, target);
        // Пасстру-блок: тело попадает в storage format «как есть» (StorageRenderer обрабатывает "pass").
        String block = "++++\n" + includeBlock(title) + "\n++++";
        reader.pushInclude(block, target, target, 1, attributes);
    }

    private static Path resolve(Document document, String target) {
        Object docdir = document.getAttribute("docdir");
        Path base = docdir == null ? Path.of(".") : Path.of(docdir.toString());
        return base.resolve(target).normalize();
    }

    /**
     * Заголовок включаемой страницы + сам макрос «Include Page». Заголовок (кликабельная ссылка на
     * страницу) нужен, чтобы несколько вставок подряд не сливались в сплошной текст.
     */
    static String includeBlock(String title) {
        return "<h2><ac:link><ri:page ri:content-title=\"" + escapeAttr(title) + "\"/>"
                + "<ac:link-body>" + escape(title) + "</ac:link-body></ac:link></h2>"
                + includeMacro(title);
    }

    /** Макрос Confluence «Include Page» со ссылкой на страницу по её заголовку. */
    static String includeMacro(String title) {
        return "<ac:structured-macro ac:name=\"include\"><ac:parameter ac:name=\"\">"
                + "<ac:link><ri:page ri:content-title=\"" + escapeAttr(title) + "\"/></ac:link>"
                + "</ac:parameter></ac:structured-macro>";
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String escapeAttr(String s) {
        return escape(s).replace("\"", "&quot;");
    }
}
