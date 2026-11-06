package io.github.adoct.generate.asciidoc;

import org.junit.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Пути из текста документа ({@code `stories/client-*.adoc`}, URL картинки и т.п.) не должны валить публикацию,
 * если строка не годится как путь. Символы, запрещённые в путях Windows, отсекаются на любой ОС — чтобы
 * результат публикации не зависел от того, где её запустили.
 */
public class DocPathsTest {

  @Test
  public void resolve_shouldReturnEmpty_whenPathHasWindowsForbiddenChar() {
    Optional<Path> result = DocPaths.resolve(Path.of("/docs"), "stories/client-*.adoc");

    assertTrue(result.isEmpty());
  }

  @Test
  public void resolve_shouldReturnEmpty_whenPathHasNulChar() {
    Optional<Path> result = DocPaths.resolve(Path.of("/docs"), "stories/client\0.adoc");

    assertTrue(result.isEmpty());
  }

  @Test
  public void resolve_shouldResolveAgainstBase_whenPathIsValid() {
    Optional<Path> result = DocPaths.resolve(Path.of("/docs/a"), "../b/page.adoc");

    assertEquals(Optional.of(Path.of("/docs/b/page.adoc")), result);
  }

  @Test
  public void resolve_shouldReturnRelativePath_whenBaseIsNull() {
    Optional<Path> result = DocPaths.resolve(null, "page.adoc");

    assertEquals(Optional.of(Path.of("page.adoc")), result);
  }

  @Test
  public void fileName_shouldTakeLastSegment_withoutParsingAsPath() {
    assertEquals("client-*.adoc", DocPaths.fileName("premium-banking/stories/client-*.adoc"));
    assertEquals("x.png", DocPaths.fileName("C:\\img\\x.png"));
    assertEquals("x.png", DocPaths.fileName("https://example.com/img/x.png"));
  }
}
