package io.github.adoct.parser.model;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Страницы, попавшие в выгрузку, — на них можно сослаться локальным файлом вместо URL Confluence.
 * <p>
 * Ссылки на страницы приходят в теле в разном виде: «сырой» {@code <a href>} по {@code pageId},
 * «человеческий» {@code /display/SPACE/Page+Title}, резолв {@code <ac:link>} через rendered view.
 * {@link #href(String)} приводит любой из них либо к относительному пути на {@code index.<ext>}
 * целевой страницы (если она есть в выгрузке), либо к абсолютному URL Confluence.
 *
 * @param byId          id страницы → её папка относительно корня выгрузки
 * @param byTitle       заголовок страницы → её папка относительно корня выгрузки
 * @param currentFolder папка текущей страницы относительно корня выгрузки (от неё считается путь)
 * @param indexFileName имя index-файла целевого формата ({@code index.adoc} / {@code index.md})
 * @param baseUrl       базовый URL Confluence — чтобы достроить ссылки, относительные от хоста
 */
public record LocalPages(Map<String, Path> byId, Map<String, Path> byTitle,
                         Path currentFolder, String indexFileName, String baseUrl) {

  private static final LocalPages EMPTY = new LocalPages(Map.of(), Map.of(), null, "index.adoc", "");

  /** Выгрузка без карты страниц: ничего не резолвится локально и не достраивается. */
  public static LocalPages empty() {
    return EMPTY;
  }

  /**
   * Адрес ссылки для документа: путь к локальному файлу, если страница входит в выгрузку, иначе —
   * абсолютный URL Confluence (rendered view и тело страницы отдают ссылки относительными от хоста,
   * и в файле такая ссылка нерабочая).
   */
  public String href(String url) {
    String local = resolve(url);
    return local != null ? local : absolute(url);
  }

  private String absolute(String url) {
    if (url == null || baseUrl.isEmpty() || !url.startsWith("/") || url.startsWith("//")) {
      return url;
    }
    return baseUrl + url;
  }

  /**
   * Относительный путь на локальный файл страницы, на которую ведёт {@code url} (якорь сохраняется),
   * либо {@code null} — если это не ссылка на страницу Confluence или такой страницы нет в выгрузке.
   */
  private String resolve(String url) {
    if (url == null || url.isBlank() || currentFolder == null) {
      return null;
    }
    int hash = url.indexOf('#');
    String anchor = hash >= 0 ? url.substring(hash) : "";
    String withoutAnchor = hash >= 0 ? url.substring(0, hash) : url;
    int question = withoutAnchor.indexOf('?');
    String path = question >= 0 ? withoutAnchor.substring(0, question) : withoutAnchor;
    Map<String, String> query = parseQuery(question >= 0 ? withoutAnchor.substring(question + 1) : "");

    Path folder = lookup(byId, query.get("pageId"));
    if (folder == null) {
      folder = lookup(byTitle, query.get("title"));
    }
    if (folder == null) {
      folder = fromPath(path);
    }
    return folder == null ? null : relativeIndex(folder) + anchor;
  }

  /** Путь на {@code index.<ext>} страницы в папке {@code folder} относительно папки текущей страницы. */
  public String relativeIndex(Path folder) {
    return currentFolder.relativize(folder).resolve(indexFileName).toString().replace('\\', '/');
  }

  /**
   * Страница по пути URL: «человеческий» {@code /display/SPACE/Page+Title} (заголовок — последний
   * сегмент) и {@code /spaces/SPACE/pages/12345/…} (id — сегмент после {@code pages}).
   */
  private Path fromPath(String path) {
    String[] segments = path.split("/");
    for (int i = 0; i < segments.length - 1; i++) {
      if ("display".equals(segments[i])) {
        return lookup(byTitle, decode(segments[segments.length - 1]));
      }
      if ("pages".equals(segments[i]) && !segments[i + 1].isEmpty()
          && segments[i + 1].chars().allMatch(Character::isDigit)) {
        return lookup(byId, segments[i + 1]);
      }
    }
    return null;
  }

  /** {@code Map.of()} не допускает {@code null}-ключ, а его в ссылке может просто не быть. */
  private static Path lookup(Map<String, Path> map, String key) {
    return key == null ? null : map.get(key);
  }

  private static Map<String, String> parseQuery(String query) {
    Map<String, String> res = new HashMap<>();
    for (String pair : query.split("&")) {
      int eq = pair.indexOf('=');
      if (eq > 0) {
        res.put(pair.substring(0, eq), decode(pair.substring(eq + 1)));
      }
    }
    return res;
  }

  /** Confluence кодирует пробелы плюсом и не-ASCII процентами; битую кодировку оставляем как есть. */
  private static String decode(String value) {
    try {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return value;
    }
  }
}
