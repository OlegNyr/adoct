package ru.gitverse.adoct.parser.build.macro;

import org.apache.commons.io.FilenameUtils;
import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.build.BlockBuilder;
import ru.gitverse.adoct.parser.build.BuildContext;

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
        StringBuilder sb = new StringBuilder();
        if (title != null) {
            sb.append('.').append(title).append('\n');
        }
        sb.append("[plantuml, format=\"png\"]\n----\n");
        String fileName = (title == null ? "plantuml" : FilenameUtils.normalize(title)) + "_%d.puml".formatted(index++);
        sb.append(externalize(text, ctx, fileName));
        sb.append("\n----");
        return List.of(new Block.RawBlock(sb.toString()));
    }
}
