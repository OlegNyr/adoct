package io.github.adoct.parser.model;

import org.junit.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/** Разбор ссылок Confluence в локальные пути выгрузки — на формах URL из реальных страниц. */
public class LocalPagesTest {

  private static final Map<String, Path> BY_ID = Map.of(
      "7378879", Path.of("О Банке", "Лицензии Банка"),
      "4167463", Path.of("База знаний"));
  private static final Map<String, Path> BY_TITLE = Map.of(
      "Лицензии Банка", Path.of("О Банке", "Лицензии Банка"),
      "Touch Bank", Path.of("О Банке", "Touch Bank"));

  private static LocalPages pages() {
    return new LocalPages(BY_ID, BY_TITLE, Path.of("О Банке"), "index.adoc", "https://confluence");
  }

  @Test
  public void resolvesAbsoluteViewpageUrlByPageId() {
    assertEquals("Лицензии Банка/index.adoc",
        pages().href("https://confluence/pages/viewpage.action?pageId=7378879"));
  }

  @Test
  public void resolvesRelativeViewpageUrlByPageId() {
    assertEquals("../База знаний/index.adoc", pages().href("/pages/viewpage.action?pageId=4167463"));
  }

  @Test
  public void resolvesDisplayUrlByTitleWithPlusEncoding() {
    assertEquals("Touch Bank/index.adoc", pages().href("https://confluence/display/bzddipk/Touch+Bank"));
  }

  @Test
  public void keepsAnchorOnResolvedPath() {
    assertEquals("Лицензии Банка/index.adoc#раздел",
        pages().href("/pages/viewpage.action?pageId=7378879#раздел"));
  }

  @Test
  public void makesUnknownConfluenceUrlAbsolute() {
    assertEquals("https://confluence/pages/viewpage.action?pageId=999",
        pages().href("/pages/viewpage.action?pageId=999"));
  }

  @Test
  public void leavesExternalUrlUntouched() {
    assertEquals("https://example.com/doc", pages().href("https://example.com/doc"));
  }

  @Test
  public void emptyMapResolvesNothing() {
    assertEquals("/pages/viewpage.action?pageId=7378879",
        LocalPages.empty().href("/pages/viewpage.action?pageId=7378879"));
  }
}
