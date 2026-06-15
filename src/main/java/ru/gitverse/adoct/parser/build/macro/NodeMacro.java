package ru.gitverse.adoct.parser.build.macro;

import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.build.BuildContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Обработчик одного Confluence-макроса ({@code <ac:structured-macro>}) — строит блоки AST.
 * Аналог старого {@code ParseMacros}, но возвращает {@link Block} вместо печати в writer.
 * Реализации регистрируются по именам ({@link #names()}) в {@link ru.gitverse.adoct.parser.build.MacroBuilder}.
 */
public interface NodeMacro {

    /** Имена макросов, которые обрабатывает этот хендлер (значения {@code ac:name}). */
    Set<String> names();

    /**
     * @param name   фактическое имя макроса (важно, когда хендлер обслуживает несколько — note/warning, ui-tabs/ui-tab)
     * @param params параметры макроса ({@code ac:parameter} имя→html)
     * @param body   тело макроса ({@code ac:rich-text-body}/{@code ac:plain-text-body}), может быть {@code null}
     */
    List<Block> build(String name, Map<String, String> params, Element body, BuildContext ctx);
}
