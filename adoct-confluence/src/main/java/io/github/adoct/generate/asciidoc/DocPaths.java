package io.github.adoct.generate.asciidoc;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Пути, взятые из текста документа: цели ссылок, упоминания {@code `file.adoc`}, картинки, include.
 *
 * <p>Такая строка не обязана быть путём: {@code `stories/client-*.adoc`} — это шаблон, {@code https://…} — URL.
 * {@link Path#of} на Windows бросает на них {@link InvalidPathException}, и раньше это роняло всю публикацию.
 * Здесь строка, не годная как путь, превращается в «пути нет», а вызывающий оставляет текст как есть.
 *
 * <p>Символы, запрещённые в путях Windows, отсекаются на любой ОС, чтобы результат публикации не зависел от
 * того, где её запустили.
 */
public final class DocPaths {

  /** Запрещены в имени файла на Windows (двоеточие не здесь: оно законно в {@code C:\}). */
  private static final Pattern FORBIDDEN = Pattern.compile("[*?<>|\"\\x00]");

  private DocPaths() {
  }

  /**
   * Разрешает {@code raw} относительно {@code base} и нормализует.
   *
   * @param base папка, от которой считается путь; {@code null} — путь остаётся относительным
   * @param raw  путь из документа
   * @return путь, либо пусто, если {@code raw} не годится как путь
   */
  public static Optional<Path> resolve(Path base, String raw) {
    if (raw == null || FORBIDDEN.matcher(raw).find()) {
      return Optional.empty();
    }
    try {
      Path path = Path.of(raw);
      return Optional.of(base == null ? path : base.resolve(path).normalize());
    } catch (InvalidPathException e) {
      return Optional.empty();
    }
  }

  /** Последний сегмент {@code raw} (после {@code /} или {@code \}) — без разбора строки как пути. */
  public static String fileName(String raw) {
    int slash = Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\'));
    return raw.substring(slash + 1);
  }
}
