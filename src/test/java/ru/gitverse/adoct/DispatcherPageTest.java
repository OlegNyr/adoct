package ru.gitverse.adoct;

import org.junit.Ignore;
import org.junit.Test;
import ru.gitverse.adoct.client.ConfluenceClient;
import ru.gitverse.adoct.client.ObjectMapperExt;

import java.nio.file.Path;

@Ignore("Интеграционный: требует живой Confluence и CONFLUENCE_TOKEN. Запускать вручную.")
public class DispatcherPageTest {
    @Test
    public void generate() {
        ConfluenceClient client = new ConfluenceClient("https://confluence.example.com",
                System.getenv("CONFLUENCE_TOKEN"));
        DispatcherPage dispatcherPage = new DispatcherPage(client,
                Path.of("D:\\AsciiDocTools\\generate"),
                ObjectMapperExt.INSTANT);
        dispatcherPage.generate("3495002552", (text, v) -> {
        });
    }
}