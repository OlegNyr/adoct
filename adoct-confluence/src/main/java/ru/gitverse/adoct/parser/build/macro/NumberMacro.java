package ru.gitverse.adoct.parser.build.macro;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.build.BlockBuilder;
import ru.gitverse.adoct.parser.build.BuildContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Макрос {@code numberedheadings} → атрибут {@code :sectnums:} (+ опц. {@code :sectnumslevels:}) перед телом. */
public final class NumberMacro extends AbstractNodeMacro {

    public NumberMacro(BlockBuilder blocks) {
        super(blocks);
    }

    @Override
    public Set<String> names() {
        return Set.of("numberedheadings");
    }

    @Override
    public List<Block> build(String name, Map<String, String> params, Element body, BuildContext ctx) {
        StringBuilder attrs = new StringBuilder(":sectnums:");
        String levels = params.get("start-numbering-with");
        if (StringUtils.isNotEmpty(levels)) {
            attrs.append("\n:sectnumslevels: ").append(levels);
        }
        return prepend(new Block.RawBlock(attrs.toString()), children(body, ctx));
    }
}
