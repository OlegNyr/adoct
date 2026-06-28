package ru.gitverse.adoct.parser.build.macro;

import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.build.BlockBuilder;
import ru.gitverse.adoct.parser.build.BuildContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Шаги: {@code ui-steps} — контейнер; {@code ui-step} — разделитель {@code ---} + тело. */
public final class StepMacro extends AbstractNodeMacro {

    public StepMacro(BlockBuilder blocks) {
        super(blocks);
    }

    @Override
    public Set<String> names() {
        return Set.of("ui-steps", "ui-step");
    }

    @Override
    public List<Block> build(String name, Map<String, String> params, Element body, BuildContext ctx) {
        if ("ui-steps".equalsIgnoreCase(name)) {
            return children(body, ctx);
        }
        return prepend(new Block.RawBlock("---"), children(body, ctx));
    }
}
