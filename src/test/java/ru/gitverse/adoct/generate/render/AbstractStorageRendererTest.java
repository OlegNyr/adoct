package ru.gitverse.adoct.generate.render;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.asciidoctor.ast.Document;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.xml.sax.InputSource;
import ru.gitverse.adoct.generate.model.RenderResult;

import javax.xml.parsers.DocumentBuilderFactory;

import java.io.StringReader;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * База для тестов {@link StorageRenderer} (AsciiDoc AST → Confluence storage format).
 * <p>
 * Один экземпляр AsciiDoctor на класс-наследник (создаётся/закрывается в {@link BeforeClass}/{@link AfterClass}).
 * Наследники сгруппированы по типам разметки; общий хелпер {@link #render} и ассерты живут здесь.
 */
public abstract class AbstractStorageRendererTest {

    private static Asciidoctor asciidoctor;

    @BeforeClass
    public static void startAsciidoctor() {
        asciidoctor = Asciidoctor.Factory.create();
    }

    @AfterClass
    public static void stopAsciidoctor() {
        asciidoctor.close();
    }

    protected RenderResult render(String adoc) {
        return render(adoc, "");
    }

    protected RenderResult render(String adoc, String imagesDir) {
        Document doc = asciidoctor.load(adoc, Options.builder().safe(SafeMode.UNSAFE).build());
        return new StorageRenderer("plantuml", Path.of("."), imagesDir).render(doc);
    }

    /** При провале печатает весь XHTML — так понятно, что именно отрендерилось. */
    protected static void assertContains(String xhtml, String fragment) {
        assertTrue(xhtml, xhtml.contains(fragment));
    }

    protected static void assertNotContains(String xhtml, String fragment) {
        assertFalse(xhtml, xhtml.contains(fragment));
    }

    /** Проверяет, что фрагмент storage format — корректный XML (теги закрыты и не перекрываются). */
    protected static void assertWellFormedXml(String xhtml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.newDocumentBuilder().parse(new InputSource(new StringReader("<root>" + xhtml + "</root>")));
    }
}
