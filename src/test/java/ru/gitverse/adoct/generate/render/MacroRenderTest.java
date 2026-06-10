package ru.gitverse.adoct.generate.render;

import org.junit.Test;

/** Листинги: PlantUML → серверный макрос, обычный листинг → макрос code (тело в CDATA). */
public class MacroRenderTest extends AbstractStorageRendererTest {

    @Test
    public void plantumlBecomesMacro() {
        String adoc = """
                [plantuml]
                ----
                @startuml
                A -> B
                @enduml
                ----
                """;
        String xhtml = render(adoc).xhtml();
        assertContains(xhtml, "<ac:structured-macro ac:name=\"plantuml\">");
        assertContains(xhtml, "<![CDATA[@startuml\nA -> B\n@enduml]]>");
    }

    @Test
    public void plainListingBecomesCodeMacro() {
        String adoc = """
                ----
                System.out.println("hi");
                ----
                """;
        String xhtml = render(adoc).xhtml();
        assertContains(xhtml, "<ac:structured-macro ac:name=\"code\">");
    }
}
