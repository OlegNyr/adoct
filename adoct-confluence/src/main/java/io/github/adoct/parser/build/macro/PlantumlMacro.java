package io.github.adoct.parser.build.macro;

import org.apache.commons.io.FilenameUtils;
import org.jsoup.nodes.Element;
import io.github.adoct.parser.ast.Block;
import io.github.adoct.parser.build.BlockBuilder;
import io.github.adoct.parser.build.BuildContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Макрос {@code plantuml} → {@code [plantuml, format="png"]}-блок; длинная диаграмма выносится в {@code .puml}. */
public final class PlantumlMacro extends AbstractNodeMacro {

    private int index = 1;

    public PlantumlMacro(BlockBuilder blocks) {
        super(blocks);
    }

    @Override
    public Set<String> names() {
        return Set.of("plantuml");
    }

    @Override
    public List<Block> build(String name, Map<String, String> params, Element body, BuildContext ctx) {
        String text = body.text();
        String title = blankToNull(params.get("title"));
        String fileName = (title == null ? "plantuml" : FilenameUtils.normalize(title)) + "_%d.puml".formatted(index++);
        String includePath = externalizePath(text, ctx, fileName);
        return List.of(new Block.CodeBlock("plantuml", title, text, includePath));
    }
}
