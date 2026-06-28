package ru.gitverse.adoct.parser.build.macro;

import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.build.BlockBuilder;
import ru.gitverse.adoct.parser.build.BuildContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Макросы {@code drawio}/{@code inc-drawio} → готовит редактируемый {@code .drawio.png}/{@code .drawio.svg} и даёт блок {@code image::}. */
public final class DrawioMacro extends AbstractNodeMacro {

    public DrawioMacro(BlockBuilder blocks) {
        super(blocks);
    }

    @Override
    public Set<String> names() {
        return Set.of("drawio", "inc-drawio");
    }

    @Override
    public List<Block> build(String name, Map<String, String> params, Element body, BuildContext ctx) {
        return List.of(DrawioRenderer.render(params.get("diagramName"), ctx.metadata()));
    }
}
