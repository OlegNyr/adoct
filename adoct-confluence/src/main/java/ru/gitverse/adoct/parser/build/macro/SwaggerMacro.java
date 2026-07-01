package ru.gitverse.adoct.parser.build.macro;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.build.BlockBuilder;
import ru.gitverse.adoct.parser.build.BuildContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Макрос Open API (Swagger) Editor → спецификация OpenAPI из тела макроса выносится в отдельный файл
 * ({@code files/swagger_N.json|yaml}), а в документ ставится ссылка на него. Интерактивный Swagger UI
 * статически не воспроизводится; сохраняем исходную спеку и даём на неё ссылку.
 * В in-memory режиме файл не пишется — спека инлайнится как {@code [source]}-блок.
 */
public final class SwaggerMacro extends AbstractNodeMacro {

    private int index = 1;

    public SwaggerMacro(BlockBuilder blocks) {
        super(blocks);
    }

    @Override
    public Set<String> names() {
        return Set.of("swagger", "open-api");
    }

    @Override
    public List<Block> build(String name, Map<String, String> params, Element body, BuildContext ctx) {
        String spec = body == null ? "" : body.wholeText().strip();
        if (StringUtils.isBlank(spec)) {
            return List.of();
        }
        String lang = spec.startsWith("{") ? "json" : "yaml";
        String path = storeFile(spec, ctx, "swagger_%d.%s".formatted(index++, lang));
        if (path == null) {
            return List.of(new Block.RawBlock("[source, %s]\n----\n%s\n----".formatted(lang, spec)));
        }
        return List.of(new Block.RawBlock("link:%s[OpenAPI спецификация (swagger)]".formatted(path)));
    }
}
