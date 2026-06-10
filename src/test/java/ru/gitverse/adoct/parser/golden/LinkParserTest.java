package ru.gitverse.adoct.parser.golden;

import org.junit.Test;
import ru.gitverse.adoct.LinksPage;
import ru.gitverse.adoct.LinksValue;
import ru.gitverse.adoct.MetadataKey;
import ru.gitverse.adoct.client.LinkResult;

import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertTrue;

/** Ссылки Confluence (ac:link): резолв страницы по метаданным и mock-фолбэк. */
public class LinkParserTest extends AbstractConvertParserTest {

    @Test
    public void resolvedPageLink() throws IOException {
        Map<LinksValue, LinkResult> links = Map.of(
                new LinksPage("Целевая страница", "DS"),
                new LinkResult("Целевая страница", "https://wiki/target"));
        String out = convert(
                "<p><ac:link><ri:page ri:content-title=\"Целевая страница\" ri:space-key=\"DS\"/></ac:link></p>",
                Map.of(MetadataKey.LINKS, links));
        assertTrue(out.contains("link:https://wiki/target[Целевая страница]"));
    }

    @Test
    public void unresolvedLinkFallsBackToMock() throws IOException {
        String out = convert(
                "<p><ac:link><ri:user ri:userkey=\"u1\"/></ac:link></p>");
        assertTrue(out.contains("link:http://mock["));
    }
}
