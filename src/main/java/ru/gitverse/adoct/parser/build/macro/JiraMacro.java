package ru.gitverse.adoct.parser.build.macro;

import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.ast.Inline;
import ru.gitverse.adoct.parser.build.BlockBuilder;
import ru.gitverse.adoct.parser.build.BuildContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Макрос {@code jira} → ссылка на задачу. На блочном уровне оборачивается в абзац. */
public final class JiraMacro extends AbstractNodeMacro {

    public JiraMacro(BlockBuilder blocks) {
        super(blocks);
    }

    @Override
    public Set<String> names() {
        return Set.of("jira");
    }

    @Override
    public List<Block> build(String name, Map<String, String> params, Element body, BuildContext ctx) {
        String link = "link:https://jira.example.com/browse/%s[]".formatted(params.get("key"));
        return List.of(new Block.Paragraph(List.of(new Inline.Raw(link))));
    }
}
