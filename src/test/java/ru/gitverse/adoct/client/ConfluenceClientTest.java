package ru.gitverse.adoct.client;

import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Ignore("Интеграционный: требует живой Confluence и CONFLUENCE_TOKEN. Запускать вручную.")
public class ConfluenceClientTest {

    private String token = System.getenv("CONFLUENCE_TOKEN");
    private String host = "https://confluence.example.com";
    private ConfluenceClient confluenceClient;

    @Before
    public void setUp() throws Exception {
        confluenceClient = new ConfluenceClient(host, token);
        confluenceClient.verifyToken();
    }

    @Test
    public void loadContent() {
        ContentPage mainPage = confluenceClient.getMainPage("21254803741");
        System.out.println(mainPage.content());
    }

    @Test
    public void resolveUrl() {
        List<LinkResult> mainPage = confluenceClient.search(
                "[ЕФС][ССД] Особенности работы с ССД в Sandbox блоке (20.1, 4.2)", null);
        System.out.println(mainPage);
    }

    @Test
    public void resolveUrlKey() {
        List<LinkResult> res = confluenceClient.search("УБ-Broker.ФО.04.Автоматизация торговли.Инвесткопилка.v2 2025 Q4 (Инкремент)",
                "KA");
        System.out.println(res);
    }

    @Test
    public void resolveUser() {
        var mainPage = confluenceClient.user("8a93ddc18f9278c9018f94a3eab30030");
        System.out.println(mainPage);
    }

    @Test
    public void name() {
        Map<String, LinkResult> attachments = confluenceClient.getAttachments("21254803741");
        attachments.forEach((k, v) -> System.out.println(v));
    }

    @SneakyThrows
    @Test
    public void loadAttachment() {
        ContentPage mainPage = confluenceClient.getMainPage("21497584874");
        Path tempDirectory = Files.createTempDirectory("confluec");
        System.out.println("Create directory " + tempDirectory);
        for (Map.Entry<String, LinkResult> entry : mainPage.attachment().entrySet()) {
            Path file = tempDirectory.resolve(entry.getKey());
            byte[] bytes = confluenceClient.downLoad(entry.getValue().url());
            Files.write(file, bytes);
        }
    }

}