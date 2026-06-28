package ru.gitverse.adoct.parser.build;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.build.macro.AnchorMacro;
import ru.gitverse.adoct.parser.build.macro.CodeMacro;
import ru.gitverse.adoct.parser.build.macro.DrawioMacro;
import ru.gitverse.adoct.parser.build.macro.ExpandMacro;
import ru.gitverse.adoct.parser.build.macro.IgnoreOkMacro;
import ru.gitverse.adoct.parser.build.macro.JiraMacro;
import ru.gitverse.adoct.parser.build.macro.NodeMacro;
import ru.gitverse.adoct.parser.build.macro.NoteMacro;
import ru.gitverse.adoct.parser.build.macro.NumberMacro;
import ru.gitverse.adoct.parser.build.macro.PlantumlMacro;
import ru.gitverse.adoct.parser.build.macro.StepMacro;
import ru.gitverse.adoct.parser.build.macro.TabsMacro;
import ru.gitverse.adoct.parser.build.macro.TocMacro;

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

    public MacroBuilder(BlockBuilder blocks) {
        List<NodeMacro> handlers = List.of(
                new IgnoreOkMacro(blocks),
                new ExpandMacro(blocks),
                new JiraMacro(blocks),
                new NumberMacro(blocks),
                new DrawioMacro(blocks),
                new CodeMacro(blocks),
                new PlantumlMacro(blocks),
                new NoteMacro(blocks),
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
            log.warn("Ignore macros {} params {}", name, params);
            return List.of();
        }
        return handler.build(name, params, body, ctx);
    }
}
