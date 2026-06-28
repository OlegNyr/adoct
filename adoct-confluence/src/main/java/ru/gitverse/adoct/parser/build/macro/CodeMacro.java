package ru.gitverse.adoct.parser.build.macro;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.build.BlockBuilder;
import ru.gitverse.adoct.parser.build.BuildContext;

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
        StringBuilder sb = new StringBuilder();
        if (title != null) {
            sb.append('.').append(title).append('\n');
        }
        sb.append(language == null ? "[source]" : "[source, %s]".formatted(language)).append("\n----\n");
        sb.append(externalize(text, ctx, makeFileName(title, language)));
        sb.append("\n----");
        return List.of(new Block.RawBlock(sb.toString()));
    }

    private String makeFileName(String title, String language) {
        String base = title == null ? "include_file_%d".formatted(index++)
                : FilenameUtils.normalize(title) + "_%d".formatted(index++);
        return language == null ? base : base + "." + language;
    }
}
