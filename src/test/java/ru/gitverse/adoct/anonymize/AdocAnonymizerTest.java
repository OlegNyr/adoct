package ru.gitverse.adoct.anonymize;

import org.junit.Assert;
import org.junit.Test;

public class AdocAnonymizerTest {

    private String anonymize(String adoc) {
        return new AdocAnonymizer(new Anonymizer(7L)).anonymize(adoc);
    }

    @Test
    public void preservesStructureAndScrubsProse() {
        String input = """
                = Секретный проект Альфа
                :confluency-id: 12345

                == Описание раздела

                Совершенно секретный абзац про деньги.

                image::тайныйскриншот.png[alt]
                """;
        String out = anonymize(input);

        // разметка и структура сохранены
        Assert.assertTrue(out.contains("= "));
        Assert.assertTrue(out.contains("== "));
        Assert.assertTrue(out.contains(":confluency-id:"));
        Assert.assertTrue(out.contains("image::"));
        Assert.assertTrue(out.contains(".png["));

        // рабочий/персональный текст вычищен
        Assert.assertFalse(out.contains("Альфа"));
        Assert.assertFalse(out.contains("Совершенно"));
        Assert.assertFalse(out.contains("деньги"));
        Assert.assertFalse(out.contains("тайныйскриншот"));
    }

    @Test
    public void keepsBlockAttributeLinesVerbatim() {
        String input = """
                [source,java]
                ----
                String secret = "пароль";
                ----

                [NOTE]
                ====
                Боевой комментарий
                ====
                """;
        String out = anonymize(input);

        // строки-атрибуты блока (нужны парсеру) — без изменений
        Assert.assertTrue(out.contains("[source,java]"));
        Assert.assertTrue(out.contains("[NOTE]"));
        // разделители блоков сохранены
        Assert.assertTrue(out.contains("----"));
        Assert.assertTrue(out.contains("===="));
        // тело кода и текст вычищены
        Assert.assertFalse(out.contains("пароль"));
        Assert.assertFalse(out.contains("Боевой"));
    }

    @Test
    public void preservesTableMarkup() {
        String input = """
                [cols="2"]
                |===
                | Имя клиента | Сумма договора
                |===
                """;
        String out = anonymize(input);
        Assert.assertTrue(out.contains("[cols=\"2\"]"));
        Assert.assertTrue(out.contains("|==="));
        Assert.assertTrue(out.contains("|"));
        Assert.assertFalse(out.contains("клиента"));
        Assert.assertFalse(out.contains("договора"));
    }

    @Test
    public void sameWordMapsToSameReplacement() {
        // согласованность: одно и то же слово — одна подмена в пределах прогона (важно для якорей/ссылок)
        String out = anonymize("платёж раз платёж два платёж\n");
        String[] words = out.strip().split("\\s+");
        Assert.assertNotEquals("платёж", words[0]);
        Assert.assertEquals(words[0], words[2]);
        Assert.assertEquals(words[2], words[4]);
    }
}
