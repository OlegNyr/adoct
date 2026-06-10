package ru.gitverse.adoct.anonymize;

import net.datafaker.Faker;
import net.datafaker.service.RandomService;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Источник согласованных подменных данных для анонимизации экспорта Confluence.
 *
 * <p>Один экземпляр на один экспорт: одинаковое исходное значение всегда подменяется
 * одним и тем же подставным (через словари-кэши), чтобы перекрёстные ссылки между
 * body/view/json/вложениями оставались согласованными.
 *
 * <p>Детерминирован: Datafaker засеян фиксированным {@link Random}, поэтому повторный
 * прогон над тем же входом даёт тот же результат. Имена/компании/логины берутся из
 * русской локали Datafaker; свободный текст — из встроенного русского словаря
 * (у Datafaker нет русского lorem).
 */
public class Anonymizer {

    private static final String[] WORDS = {
            "система", "процесс", "документ", "сервис", "запрос", "ответ", "данные",
            "параметр", "значение", "модуль", "компонент", "интерфейс", "событие",
            "операция", "результат", "условие", "правило", "схема", "модель", "поле",
            "таблица", "список", "раздел", "описание", "пример", "настройка", "статус",
            "версия", "источник", "получатель", "сообщение", "очередь", "ключ", "метод",
            "клиент", "сервер", "база", "хранилище", "отчёт", "форма", "шаблон", "поток",
            "задача", "шаг", "этап", "проверка", "контроль", "доступ", "роль", "право"
    };

    private final Random random;
    private final Faker faker;
    private final AtomicInteger fileCounter = new AtomicInteger();

    private final Map<String, String> names = new ConcurrentHashMap<>();
    private final Map<String, String> logins = new ConcurrentHashMap<>();
    private final Map<String, String> userKeys = new ConcurrentHashMap<>();
    private final Map<String, String> titles = new ConcurrentHashMap<>();
    private final Map<String, String> spaceKeys = new ConcurrentHashMap<>();
    private final Map<String, String> jiraKeys = new ConcurrentHashMap<>();
    private final Map<String, String> anchors = new ConcurrentHashMap<>();
    private final Map<String, String> baseNames = new ConcurrentHashMap<>();

    public Anonymizer() {
        this(42L);
    }

    public Anonymizer(long seed) {
        this.random = new Random(seed);
        this.faker = new Faker(new Locale("ru"), new RandomService(random));
    }

    /** ФИО человека (для отображаемых имён и текстов ссылок на пользователей). */
    public String name(String original) {
        return map(names, original, faker.name()::fullName);
    }

    /** Логин/username (для ссылок вида /display/~login). */
    public String login(String original) {
        return map(logins, original, () -> "user" + (random.nextInt(900000) + 100000));
    }

    /** Непрозрачный ключ пользователя Confluence (хеш). */
    public String userKey(String original) {
        return map(userKeys, original, () -> randomHex(original == null ? 32 : Math.max(8, original.length())));
    }

    /** Заголовок страницы/документа. */
    public String title(String original) {
        return map(titles, original, () -> capitalize(words(2 + random.nextInt(3))));
    }

    /** Ключ пространства Confluence. */
    public String spaceKey(String original) {
        return map(spaceKeys, original, () -> randomUpper(2 + random.nextInt(6)));
    }

    /** Ключ задачи Jira, напр. ABC-123. */
    public String jiraKey(String original) {
        return map(jiraKeys, original, () -> randomUpper(2 + random.nextInt(3)) + "-" + (random.nextInt(9000) + 100));
    }

    /** Имя якоря (slug, безопасный для AsciiDoc). */
    public String anchor(String original) {
        return map(anchors, original, () -> "anchor_" + (random.nextInt(9000) + 1000));
    }

    /**
     * Базовое имя вложения (без расширения), согласованное между ссылками и файлами.
     * Латиница — чтобы имя файла было безопасным для ФС/архива.
     */
    public String baseName(String originalBase) {
        return map(baseNames, originalBase, () -> "file_" + fileCounter.incrementAndGet());
    }

    /**
     * Имя файла вложения: подменяет базовую часть через {@link #baseName(String)},
     * сохраняя известный суффикс/расширение (включая составные вроде {@code .drawio.png}).
     */
    public String fileName(String originalName) {
        if (StringUtils.isBlank(originalName)) {
            return originalName;
        }
        String[] suffixes = {".drawio.png", ".drawio", ".png", ".jpeg", ".jpg", ".gif",
                ".svg", ".puml", ".json", ".xml", ".pdf", ".docx", ".xlsx"};
        for (String suffix : suffixes) {
            if (StringUtils.endsWithIgnoreCase(originalName, suffix)) {
                String base = originalName.substring(0, originalName.length() - suffix.length());
                return baseName(base) + suffix;
            }
        }
        return baseName(originalName);
    }

    /** Дата в ISO-подобном виде (для datetime/created). */
    public String date(String original) {
        int year = 2020 + random.nextInt(5);
        int month = 1 + random.nextInt(12);
        int day = 1 + random.nextInt(28);
        return "%04d-%02d-%02d".formatted(year, month, day);
    }

    /**
     * Однострочный текст: сохраняет количество слов и ведущие/замыкающие пробелы исходника,
     * заменяя содержимое русскими словами. Пустой/пробельный текст не меняется.
     */
    public String inlineText(String original) {
        if (original == null || original.isBlank()) {
            return original;
        }
        String lead = leading(original);
        String trail = trailing(original);
        String core = original.substring(lead.length(), original.length() - trail.length());
        int count = Math.max(1, core.split("\\s+").length);
        return lead + capitalize(words(count)) + trail;
    }

    /**
     * Многострочный текст (тело code/plantuml): сохраняет число строк и пустые строки,
     * чтобы у конвертера срабатывала та же логика (вынос больших блоков в файл).
     */
    public String multilineText(String original) {
        if (original == null || original.isBlank()) {
            return original;
        }
        String[] lines = original.split("\n", -1);
        StringBuilder sb = new StringBuilder(original.length());
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            String line = lines[i];
            if (line.isBlank()) {
                sb.append(line);
            } else {
                int count = Math.max(1, line.trim().split("\\s+").length);
                sb.append(words(count));
            }
        }
        return sb.toString();
    }

    /** Подменяет хост в URL на confluence.example.com и чистит идентифицирующие части пути/параметров. */
    public String url(String original) {
        if (StringUtils.isBlank(original)) {
            return original;
        }
        String result = original.replaceAll("https?://[^/\\s\"]+", "https://confluence.example.com");
        result = result.replaceAll("(?<=/display/~)[^/\\s\"?&]+", "user");
        result = result.replaceAll("(?<=pageId=)\\d+", String.valueOf(random.nextInt(9000000) + 1000000));
        return result;
    }

    private String words(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(WORDS[random.nextInt(WORDS.length)]);
        }
        return sb.toString();
    }

    private String randomHex(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(Integer.toHexString(random.nextInt(16)));
        }
        return sb.toString();
    }

    private String randomUpper(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ('A' + random.nextInt(26)));
        }
        return sb.toString();
    }

    private static String capitalize(String text) {
        if (text.isEmpty()) {
            return text;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String leading(String text) {
        int i = 0;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return text.substring(0, i);
    }

    private static String trailing(String text) {
        int i = text.length();
        while (i > 0 && Character.isWhitespace(text.charAt(i - 1))) {
            i--;
        }
        return text.substring(i);
    }

    private static String map(Map<String, String> cache, String original, Supplier<String> generator) {
        if (original == null) {
            return null;
        }
        return cache.computeIfAbsent(original, k -> generator.get());
    }
}
