package ru.gitverse.adoct.generate.asciidoc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Индекс якорей по набору {@code .adoc}-файлов: для каждого явного якоря ({@code [[id]]},
 * {@code [#id]}, {@code anchor:id[]}) запоминает, в каком файле он объявлен, и заголовок страницы
 * этого файла.
 *
 * <p>Нужен рендереру, чтобы отличить настоящую ссылку-якорь <b>в пределах страницы</b> от ссылки на
 * термин/раздел, который на самом деле живёт на <b>другой</b> странице. AsciiDoctor для ссылки без
 * расширения и без {@code #} (напр. {@code <<csat>>} / {@code xref:csat[]}) выдаёт {@code <a
 * href="#csat">} — то есть якорь внутри текущей страницы. Если же {@code [[csat]]} объявлен в другом
 * файле, такая ссылка должна вести на соответствующую страницу Confluence, а не на несуществующий
 * якорь текущей.
 */
public final class AnchorIndex {

    /** Цель якоря: файл-источник (абсолютный нормализованный путь) и заголовок его страницы. */
    public record Target(Path file, String title) {
    }

    /** {@code [[id]]} или {@code [[id,reftext]]}. */
    private static final Pattern DOUBLE_BRACKET = Pattern.compile("\\[\\[([A-Za-z0-9_:.\\-]+)(?:,[^\\]]*)?]]");

    /** Сокращённый блочный якорь {@code [#id]} (роли через {@code .}/{@code %} — отбрасываются). */
    private static final Pattern BLOCK_ID = Pattern.compile("\\[#([A-Za-z0-9_:\\-]+)[.%,\\]]");

    /** Инлайн-макрос {@code anchor:id[]}. */
    private static final Pattern ANCHOR_MACRO = Pattern.compile("anchor:([A-Za-z0-9_:.\\-]+)\\[");

    private final Map<String, Target> anchors;

    private AnchorIndex(Map<String, Target> anchors) {
        this.anchors = anchors;
    }

    /** Пустой индекс — ни один якорь не считается внешним (поведение «как раньше»). */
    public static AnchorIndex empty() {
        return new AnchorIndex(Map.of());
    }

    /**
     * Сканирует набор файлов и собирает индекс якорей. При дубликатах якоря в разных файлах
     * выигрывает первый встретившийся (порядок коллекции).
     */
    public static AnchorIndex scan(Collection<Path> files) {
        Map<String, Target> map = new HashMap<>();
        for (Path file : files) {
            Path norm = file.toAbsolutePath().normalize();
            Set<String> ids = extractAnchors(norm);
            if (ids.isEmpty()) {
                continue;
            }
            String title = AdocPageTitle.fromFileOrName(norm, norm.getFileName().toString());
            Target target = new Target(norm, title);
            for (String id : ids) {
                map.putIfAbsent(id, target);
            }
        }
        return new AnchorIndex(map);
    }

    /** Цель якоря или {@code null}, если такой якорь нигде не объявлен. */
    public Target lookup(String anchorId) {
        return anchors.get(anchorId);
    }

    private static Set<String> extractAnchors(Path file) {
        Set<String> ids = new LinkedHashSet<>();
        if (!Files.isRegularFile(file)) {
            return ids;
        }
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return ids;
        }
        collect(DOUBLE_BRACKET, text, ids);
        collect(BLOCK_ID, text, ids);
        collect(ANCHOR_MACRO, text, ids);
        return ids;
    }

    private static void collect(Pattern pattern, String text, Set<String> into) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            into.add(m.group(1));
        }
    }
}
