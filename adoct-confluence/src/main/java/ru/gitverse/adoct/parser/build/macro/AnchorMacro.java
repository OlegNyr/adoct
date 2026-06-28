package ru.gitverse.adoct.parser.build.macro;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.build.BlockBuilder;
import ru.gitverse.adoct.parser.build.BuildContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Макрос {@code anchor} → блочный якорь {@code [#id]} (имя в безымянном параметре; пустой не печатаем). */
public final class AnchorMacro extends AbstractNodeMacro {

    public AnchorMacro(BlockBuilder blocks) {
        super(blocks);
    }

    @Override
    public Set<String> names() {
        return Set.of("anchor");
    }

    @Override
    public List<Block> build(String name, Map<String, String> params, Element body, BuildContext ctx) {
        String anchor = params.get("");
        if (StringUtils.isBlank(anchor)) {
            anchor = params.values().stream().filter(StringUtils::isNotBlank).findFirst().orElse(null);
        }
        return StringUtils.isBlank(anchor) ? List.of() : List.of(new Block.RawBlock("[#%s]".formatted(anchor)));
    }
}
