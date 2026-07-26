package io.github.adoct.parser.build.macro;

import org.jsoup.nodes.Element;
import io.github.adoct.parser.ast.Block;
import io.github.adoct.parser.build.BlockBuilder;
import io.github.adoct.parser.build.BuildContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Page Properties ({@code details}) — контейнер со свойствами страницы, тело которого это таблица
 * «ключ/значение». Разворачиваем тело как есть (таблица строится общим {@link io.github.adoct.parser.build.tag.TableTag}
 * и рендерится и в AsciiDoc, и в Markdown), без под-заголовка. Параметры ({@code id}/{@code hidden})
 * не влияют на статический экспорт.
 */
public final class DetailsMacro extends AbstractNodeMacro {

    public DetailsMacro(BlockBuilder blocks) {
        super(blocks);
    }

    @Override
    public Set<String> names() {
        return Set.of("details");
    }

    @Override
    public List<Block> build(String name, Map<String, String> params, Element body, BuildContext ctx) {
        return children(body, ctx);
    }
}
