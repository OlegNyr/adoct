package ru.gitverse.adoct.generate.render;

import org.junit.Test;

/** Листинги: PlantUML → серверный макрос; [source] → code (+язык); прочие листинги/литералы → noformat. */
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
    public void plainListingBecomesNoformat() {
        String adoc = """
                ----
                System.out.println("hi");
                ----
                """;
        String xhtml = render(adoc).xhtml();
        assertContains(xhtml, "<ac:structured-macro ac:name=\"noformat\">");
    }

    @Test
    public void literalBecomesNoformat() {
        String xhtml = render("....\nплоский текст\n....\n").xhtml();
        assertContains(xhtml, "<ac:structured-macro ac:name=\"noformat\">");
        assertContains(xhtml, "плоский текст");
    }

    @Test
    public void sourceBlockEmitsCodeMacroWithLanguage() {
        String adoc = """
                [source,java]
                ----
                int x = 1;
                ----
                """;
        String xhtml = render(adoc).xhtml();
        assertContains(xhtml, "<ac:structured-macro ac:name=\"code\">");
        assertContains(xhtml, "<ac:parameter ac:name=\"language\">java</ac:parameter>");
        assertContains(xhtml, "<![CDATA[int x = 1;]]>");
    }

    @Test
    public void sourceLanguageIsMappedToConfluenceName() {
        String adoc = """
                [source,python]
                ----
                print(1)
                ----
                """;
        String xhtml = render(adoc).xhtml();
        // python → py
        assertContains(xhtml, "<ac:parameter ac:name=\"language\">py</ac:parameter>");
    }

    @Test
    public void unsupportedLanguageOmitsLanguageParameter() {
        String adoc = """
                [source,brainfuck]
                ----
                +++
                ----
                """;
        String xhtml = render(adoc).xhtml();
        assertContains(xhtml, "<ac:structured-macro ac:name=\"code\">");
        assertNotContains(xhtml, "ac:name=\"language\"");
    }
}
