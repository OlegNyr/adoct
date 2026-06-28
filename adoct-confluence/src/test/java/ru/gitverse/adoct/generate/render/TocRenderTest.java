package ru.gitverse.adoct.generate.render;

import org.junit.Test;

/** TOC: блок toc::[] → макрос Confluence toc с maxLevel из :toclevels:. */
public class TocRenderTest extends AbstractStorageRendererTest {

    @Test
    public void tocMacroBlockBecomesConfluenceToc() {
        String adoc = """
                = Документ
                :toc: macro
                :toclevels: 3

                toc::[]

                == Раздел
                текст
                """;
        String xhtml = render(adoc).xhtml();
        assertContains(xhtml, "<ac:structured-macro ac:name=\"toc\">");
        assertContains(xhtml, "<ac:parameter ac:name=\"maxLevel\">3</ac:parameter>");
    }

    @Test
    public void tocDefaultMaxLevelIsTwo() {
        String adoc = """
                = Документ
                :toc: macro

                toc::[]

                == Раздел
                текст
                """;
        String xhtml = render(adoc).xhtml();
        assertContains(xhtml, "<ac:parameter ac:name=\"maxLevel\">2</ac:parameter>");
    }
}
