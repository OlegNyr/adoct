package ru.gitverse.adoct.anonymize;

import org.junit.Assert;
import org.junit.Test;

public class StorageHtmlAnonymizerTest {

    private String anonymize(String html) {
        return new StorageHtmlAnonymizer(new Anonymizer(7L)).anonymizeFragment(html);
    }

    @Test
    public void preservesStructureAndScrubsPersonalData() {
        String input = """
                <h1>Секретный проект Альфа</h1>
                <p>Автор Иванов Иван</p>
                <ac:link><ri:user ri:userkey="abc123deadbeef"/></ac:link>
                <ac:structured-macro ac:name="jira"><ac:parameter ac:name="key">PROJ-42</ac:parameter></ac:structured-macro>
                <table><tbody><tr><td>Совершенно секретные данные</td></tr></tbody></table>
                """;
        String out = anonymize(input);

        // структура для парсера сохранена
        Assert.assertTrue(out.contains("<h1"));
        Assert.assertTrue(out.contains("<table"));
        Assert.assertTrue(out.contains("ac:structured-macro"));
        Assert.assertTrue(out.contains("ac:name=\"jira\""));
        Assert.assertTrue(out.contains("ri:userkey="));

        // рабочие/персональные данные вычищены
        Assert.assertFalse(out.contains("Альфа"));
        Assert.assertFalse(out.contains("Иванов"));
        Assert.assertFalse(out.contains("abc123deadbeef"));
        Assert.assertFalse(out.contains("PROJ-42"));
        Assert.assertFalse(out.contains("секретные"));
    }

    @Test
    public void keepsParserSignificantMacroParameters() {
        String input = """
                <ac:structured-macro ac:name="code">
                  <ac:parameter ac:name="language">json</ac:parameter>
                  <ac:parameter ac:name="title">Боевой конфиг продакшена</ac:parameter>
                  <ac:plain-text-body>{"token":"super-secret-value"}</ac:plain-text-body>
                </ac:structured-macro>
                """;
        String out = anonymize(input);

        // language нужен парсеру — сохраняем
        Assert.assertTrue(out.contains("json"));
        // заголовок и тело кода вычищены
        Assert.assertFalse(out.contains("Боевой"));
        Assert.assertFalse(out.contains("super-secret-value"));
    }

    @Test
    public void sameInputMapsToSameReplacement() {
        Anonymizer anon = new Anonymizer(7L);
        Assert.assertEquals(anon.title("Проект Альфа"), anon.title("Проект Альфа"));
        Assert.assertEquals(anon.userKey("key-1"), anon.userKey("key-1"));
        Assert.assertNotEquals(anon.userKey("key-1"), anon.userKey("key-2"));
    }
}
