package io.github.adoct.generate.asciidoc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Заголовок-фолбэк по имени файла не должен падать на строке, которая не годится как путь. */
public class AdocPageTitleInvalidPathTest {

  @Test
  public void fromFileOrName_shouldUseFileName_whenTargetIsInvalidPath() {
    String title = AdocPageTitle.fromFileOrName(null, "stories/client-*.adoc");

    assertEquals("client-*", title);
  }
}
