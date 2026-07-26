package io.github.adoct.parser.build;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Element;
import io.github.adoct.parser.ast.Block;
import io.github.adoct.parser.build.macro.AnchorMacro;
import io.github.adoct.parser.build.macro.ChildrenMacro;
import io.github.adoct.parser.build.macro.CodeMacro;
import io.github.adoct.parser.build.macro.DetailsMacro;
import io.github.adoct.parser.build.macro.DrawioMacro;
import io.github.adoct.parser.build.macro.ExpandMacro;
import io.github.adoct.parser.build.macro.IgnoreOkMacro;
import io.github.adoct.parser.build.macro.IncludeMacro;
import io.github.adoct.parser.build.macro.JiraMacro;
import io.github.adoct.parser.build.macro.NodeMacro;
import io.github.adoct.parser.build.macro.NoteMacro;
import io.github.adoct.parser.build.macro.NumberMacro;
import io.github.adoct.parser.build.macro.PanelMacro;
import io.github.adoct.parser.build.macro.PlantumlMacro;
import io.github.adoct.parser.build.macro.ProfileMacro;
import io.github.adoct.parser.build.macro.StatusMacro;
import io.github.adoct.parser.build.macro.StepMacro;
import io.github.adoct.parser.build.macro.SwaggerMacro;
import io.github.adoct.parser.build.macro.TabsMacro;
import io.github.adoct.parser.build.macro.TocMacro;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Диспетчер Confluence-макросов: реестр имя→{@link NodeMacro}, первый подходящий строит блоки.
 * Неизвестный макрос игнорируется с логом (как старый {@code MacrosIgnore}). Аналог
 * {@code ParseMacrosDispatcher}, но каждый макрос — в своём файле в пакете {@code build.macro}.
 */
@Slf4j
public final class MacroBuilder {

    private final Map<String, NodeMacro> byName;
    private final BlockBuilder blocks;

    public MacroBuilder(BlockBuilder blocks) {
        this.blocks = blocks;
        List<NodeMacro> handlers = List.of(
                new IgnoreOkMacro(blocks),
                new ExpandMacro(blocks),
                new DetailsMacro(blocks),
                new ChildrenMacro(blocks),
                new JiraMacro(blocks),
                new NumberMacro(blocks),
                new DrawioMacro(blocks),
                new CodeMacro(blocks),
                new PlantumlMacro(blocks),
                new NoteMacro(blocks),
                new PanelMacro(blocks),
                new ProfileMacro(blocks),
                new StatusMacro(blocks),
                new IncludeMacro(blocks),
                new SwaggerMacro(blocks),
                new AnchorMacro(blocks),
                new StepMacro(blocks),
                new TocMacro(blocks),
                new TabsMacro(blocks)
        );
        Map<String, NodeMacro> map = new HashMap<>();
        for (NodeMacro handler : handlers) {
            for (String name : handler.names()) {
                map.put(name, handler);
            }
        }
        this.byName = Map.copyOf(map);
    }

    public List<Block> build(String name, Map<String, String> params, Element body, BuildContext ctx) {
        NodeMacro handler = byName.get(name == null ? "" : name);
        if (handler == null) {
            // Неизвестный (в т.ч. сторонний плагинный) макрос с телом — разворачиваем тело, чтобы не
            // терять контент, обёрнутый в Table Filter, AURA/Mosaic-панели, Swagger и т.п. Без тела
            // (динамический листинг) — пропускаем.
            if (body != null && !body.children().isEmpty()) {
                log.info("Unknown macro {} — unwrapping body ({} params)", name, params.size());
                return blocks.build(body.children(), ctx);
            }
            log.warn("Ignore macros {} params {}", name, params);
            return List.of();
        }
        return handler.build(name, params, body, ctx);
    }
}
