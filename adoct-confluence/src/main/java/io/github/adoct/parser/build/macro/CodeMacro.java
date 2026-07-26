package io.github.adoct.parser.build.macro;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import io.github.adoct.parser.ast.Block;
import io.github.adoct.parser.build.BlockBuilder;
import io.github.adoct.parser.build.BuildContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Макрос {@code code} → {@code [source, lang]}-блок; длинный код выносится в {@code files/}. */
public final class CodeMacro extends AbstractNodeMacro {

    private int index = 1;

    public CodeMacro(BlockBuilder blocks) {
        super(blocks);
    }

    @Override
    public Set<String> names() {
        return Set.of("code");
    }

    @Override
    public List<Block> build(String name, Map<String, String> params, Element body, BuildContext ctx) {
        String text = body.text();
        String title = blankToNull(params.get("title"));
        String language = params.get("language");
        if (language == null) {
            if (StringUtils.startsWith(text, "{")) {
                language = "json";
            } else if (StringUtils.startsWith(text, "<")) {
                language = "xml";
            }
        }
        String includePath = externalizePath(text, ctx, makeFileName(title, language));
        return List.of(new Block.CodeBlock(language, title, text, includePath));
    }

    private String makeFileName(String title, String language) {
        String base = title == null ? "include_file_%d".formatted(index++)
                : FilenameUtils.normalize(title) + "_%d".formatted(index++);
        return language == null ? base : base + "." + language;
    }
}
