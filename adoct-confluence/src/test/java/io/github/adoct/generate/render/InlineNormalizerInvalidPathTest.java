package io.github.adoct.generate.render;

import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Регресс: на Windows {@code `premium-banking/stories/client-*.adoc`} ронял публикацию
 * ({@code InvalidPathException} из {@code Path.of}). Строка, не годная как путь, должна оставаться как есть.
 */
public class InlineNormalizerInvalidPathTest {

  private final InlineNormalizer normalizer = new InlineNormalizer(Path.of("."), null, null, "", null, Path.of("."));

  @Test
  public void normalize_shouldKeepCode_whenAdocMentionIsInvalidPath() {
    String xhtml = normalizer.normalize("<code>stories/client-*.adoc</code>", new ArrayList<>());

    assertTrue(xhtml, xhtml.contains("<code>stories/client"));
  }

  @Test
  public void normalize_shouldLinkByFileName_whenCrossDocTargetIsInvalidPath() {
    String xhtml = normalizer.normalize("<a href=\"stories/client-*.adoc\">клиент</a>", new ArrayList<>());

    assertTrue(xhtml, xhtml.contains("<ac:link-body>клиент</ac:link-body>"));
  }

  @Test
  public void normalize_shouldKeepLink_whenFileTargetIsInvalidPath() {
    List<Path> attachments = new ArrayList<>();

    String xhtml = normalizer.normalize("<a href=\"files/report-*.pdf\">отчёт</a>", attachments);

    assertTrue(xhtml, xhtml.contains("отчёт") && attachments.isEmpty());
  }

  @Test
  public void normalize_shouldKeepImage_whenImageSourceIsInvalidPath() {
    List<Path> attachments = new ArrayList<>();

    String xhtml = normalizer.normalize("<img src=\"img/pic-*.png\" alt=\"pic\">", attachments);

    assertTrue(xhtml, xhtml.contains("<img") && attachments.isEmpty());
  }
}
