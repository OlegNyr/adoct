package ru.gitverse.adoct.anonymize;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Анонимизация исходника AsciiDoc ({@code .adoc}) для приложения к баг-репорту импорта
 * (AsciiDoc → Confluence). Цель — сохранить структуру и разметку, по которой воспроизводится
 * поведение парсера/рендерера, и при этом вычистить рабочий/персональный текст.
 *
 * <p>Обработка построчная:
 * <ul>
 *   <li>строки-атрибуты блока ({@code [source,java]}, {@code [NOTE]}, {@code [cols="2"]}) остаются
 *       как есть — они структурные и завязаны на разбор;</li>
 *   <li>строки-атрибуты документа ({@code :name: value}) сохраняют имя, значение обфусцируется;</li>
 *   <li>остальные строки (заголовки, текст, тело кода, таблицы, списки) прогоняются через
 *       {@link Anonymizer#scrubWords(String)} — разметка/разделители/расширения сохраняются,
 *       подменяется только текст.</li>
 * </ul>
 *
 * <p>Согласованность подмен обеспечивает общий {@link Anonymizer} (один на прогон).
 */
public class AdocAnonymizer {

    /** Строка-атрибут блока целиком в квадратных скобках: {@code [source,java]}, {@code [NOTE]}, ... */
    private static final Pattern BLOCK_ATTRS = Pattern.compile("^\\s*\\[.*]\\s*$");

    /** Атрибут документа: {@code :name: value} или {@code :!name:} — имя сохраняем, значение чистим. */
    private static final Pattern ATTR_ENTRY = Pattern.compile("^(:!?[\\w.-]+:)(.*)$");

    private final Anonymizer anon;

    public AdocAnonymizer(Anonymizer anon) {
        this.anon = anon;
    }

    public String anonymize(String adoc) {
        if (StringUtils.isBlank(adoc)) {
            return adoc;
        }
        String[] lines = adoc.split("\n", -1);
        StringBuilder sb = new StringBuilder(adoc.length());
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(anonymizeLine(lines[i]));
        }
        return sb.toString();
    }

    private String anonymizeLine(String line) {
        if (BLOCK_ATTRS.matcher(line).matches()) {
            return line;
        }
        Matcher attr = ATTR_ENTRY.matcher(line);
        if (attr.matches()) {
            return attr.group(1) + anon.scrubWords(attr.group(2));
        }
        return anon.scrubWords(line);
    }
}
