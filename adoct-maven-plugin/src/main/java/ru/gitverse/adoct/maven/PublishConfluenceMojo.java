package ru.gitverse.adoct.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import ru.gitverse.adoct.generate.AdocPublisher;
import ru.gitverse.adoct.generate.confluence.ConfluenceClient;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Публикует папку (или один {@code .adoc}) в Confluence, переиспользуя движок
 * {@link AdocPublisher} из модуля {@code :adoct-confluence}.
 *
 * <p>Папка публикуется рекурсивно: {@code index.adoc} каждой папки становится страницей-узлом, остальные
 * файлы — её дочерними страницами (подробности — в Javadoc {@link AdocPublisher}). Номер целевой/родительской
 * страницы берётся из {@code pageUrl} ({@code pageId=...} или {@code /display/SPACE/Title}); для отдельных
 * файлов можно опираться на атрибут {@code :confluency-id:}.
 *
 * <p>Пример вызова: {@code mvn adoct:publish -Dconfluence.pageUrl=... -Dconfluence.token=...}.
 */
@Mojo(name = "publish", threadSafe = true)
public class PublishConfluenceMojo extends AbstractMojo {

  /** Формат итоговой строки {@link AdocPublisher} для папки содержит {@code failed <N>}. */
  private static final Pattern FAILED_COUNT = Pattern.compile("failed (\\d+)");

  /**
   * URL целевой (для файла) или родительской (для папки) страницы Confluence. Поддерживается
   * {@code ...?pageId=NNN} и «человеческий» {@code .../display/SPACE/Title}.
   */
  @Parameter(property = "confluence.pageUrl")
  private String pageUrl;

  /**
   * Базовый URL сервера Confluence (корень REST API). Если не задан — берётся из схемы и хоста
   * {@code pageUrl}. Указывайте явно, если Confluence развёрнут с контекстным путём (например,
   * {@code https://host/confluence}).
   */
  @Parameter(property = "confluence.serverUrl")
  private String serverUrl;

  /** Personal Access Token (Bearer) для доступа к Confluence. */
  @Parameter(property = "confluence.token")
  private String token;

  /** Папка с {@code .adoc} (или один {@code .adoc}-файл) для публикации. */
  @Parameter(property = "confluence.source", defaultValue = "${project.basedir}/src/docs/asciidoc", required = true)
  private File source;

  /** Пропустить выполнение цели ({@code -Dconfluence.skip=true}). */
  @Parameter(property = "confluence.skip", defaultValue = "false")
  private boolean skip;

  /** Провалить сборку, если при публикации папки часть файлов не залилась (в итоге {@code failed > 0}). */
  @Parameter(property = "confluence.failOnError", defaultValue = "true")
  private boolean failOnError;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skip) {
      getLog().info("Confluence publish skipped (confluence.skip=true).");
      return;
    }
    if (isBlank(pageUrl)) {
      throw new MojoFailureException("Missing required parameter: confluence.pageUrl");
    }
    if (isBlank(token)) {
      throw new MojoFailureException("Missing required parameter: confluence.token");
    }
    if (source == null || !source.exists()) {
      throw new MojoFailureException("Source path does not exist: " + source);
    }
    String baseUrl = deriveServerUrl(serverUrl, pageUrl);
    getLog().info("Publishing " + source + " to Confluence " + baseUrl + " (page " + pageUrl + ")");

    ConfluenceClient client = new ConfluenceClient(baseUrl, token);
    String summary;
    try {
      summary = new AdocPublisher(client)
          .progress(getLog()::info)
          .publish(pageUrl, source.toPath());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new MojoExecutionException("Publication interrupted", e);
    } catch (IOException | RuntimeException e) {
      throw new MojoExecutionException("Failed to publish to Confluence: " + e.getMessage(), e);
    }

    getLog().info(summary);
    if (failOnError && failedCount(summary) > 0) {
      throw new MojoFailureException(summary + " (set confluence.failOnError=false to ignore)");
    }
  }

  /** Базовый URL сервера: явный {@code serverUrl} или схема+хост из {@code pageUrl}. */
  static String deriveServerUrl(String serverUrl, String pageUrl) throws MojoFailureException {
    if (serverUrl != null && !serverUrl.isBlank()) {
      return serverUrl.trim();
    }
    try {
      URI uri = new URI(pageUrl);
      if (uri.getScheme() == null || uri.getAuthority() == null) {
        throw new MojoFailureException(
            "Cannot derive serverUrl from pageUrl '" + pageUrl + "'; set <serverUrl> explicitly.");
      }
      return uri.getScheme() + "://" + uri.getAuthority();
    } catch (URISyntaxException e) {
      throw new MojoFailureException(
          "Cannot derive serverUrl from pageUrl '" + pageUrl + "'; set <serverUrl> explicitly.", e);
    }
  }

  /** Число неуспешно опубликованных файлов из итоговой строки {@link AdocPublisher} (0, если нет). */
  static int failedCount(String summary) {
    Matcher matcher = FAILED_COUNT.matcher(summary);
    return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
