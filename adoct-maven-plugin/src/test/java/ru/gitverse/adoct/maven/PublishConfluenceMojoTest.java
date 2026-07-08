package ru.gitverse.adoct.maven;

import org.apache.maven.plugin.MojoFailureException;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PublishConfluenceMojoTest {

  @Test
  public void deriveServerUrl_shouldReturnExplicit_whenGiven() throws Exception {
    String base = PublishConfluenceMojo.deriveServerUrl("https://conf.example.com/confluence ",
        "https://conf.example.com/confluence/pages/viewpage.action?pageId=1");

    assertEquals("https://conf.example.com/confluence", base);
  }

  @Test
  public void deriveServerUrl_shouldTakeOrigin_whenServerUrlAbsent() throws Exception {
    String base = PublishConfluenceMojo.deriveServerUrl(null,
        "https://conf.example.com/pages/viewpage.action?pageId=12345");

    assertEquals("https://conf.example.com", base);
  }

  @Test
  public void deriveServerUrl_shouldTakeOrigin_fromDisplayUrl() throws Exception {
    String base = PublishConfluenceMojo.deriveServerUrl("  ",
        "https://conf.example.com/display/SPACE/Some+Title");

    assertEquals("https://conf.example.com", base);
  }

  @Test
  public void deriveServerUrl_shouldFail_whenPageUrlHasNoHost() {
    assertThrows(MojoFailureException.class,
        () -> PublishConfluenceMojo.deriveServerUrl(null, "pageId=42"));
  }

  @Test
  public void failedCount_shouldParseFolderSummary() {
    int failed = PublishConfluenceMojo.failedCount(
        "Folder published: created 3, updated 1, skipped 0, failed 2 (of 6 files)");

    assertEquals(2, failed);
  }

  @Test
  public void failedCount_shouldBeZero_whenAbsent() {
    assertEquals(0, PublishConfluenceMojo.failedCount("Published X.adoc (page 123)"));
  }

  @Test
  public void execute_shouldDoNothing_whenSkip() throws Exception {
    PublishConfluenceMojo mojo = new PublishConfluenceMojo();
    set(mojo, "skip", true);

    mojo.execute();
  }

  @Test
  public void execute_shouldFail_whenPageUrlMissing() throws Exception {
    PublishConfluenceMojo mojo = new PublishConfluenceMojo();
    set(mojo, "skip", false);
    set(mojo, "token", "pat");

    assertThrows(MojoFailureException.class, mojo::execute);
  }

  @Test
  public void execute_shouldFail_whenTokenMissing() throws Exception {
    PublishConfluenceMojo mojo = new PublishConfluenceMojo();
    set(mojo, "skip", false);
    set(mojo, "pageUrl", "https://conf.example.com/pages/viewpage.action?pageId=1");

    assertThrows(MojoFailureException.class, mojo::execute);
  }

  @Test
  public void execute_shouldFail_whenSourceMissing() throws Exception {
    PublishConfluenceMojo mojo = new PublishConfluenceMojo();
    set(mojo, "skip", false);
    set(mojo, "pageUrl", "https://conf.example.com/pages/viewpage.action?pageId=1");
    set(mojo, "token", "pat");
    set(mojo, "source", new File("no-such-dir-" + System.nanoTime()));

    assertThrows(MojoFailureException.class, mojo::execute);
  }

  private static void set(Object target, String field, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(field);
    f.setAccessible(true);
    f.set(target, value);
  }
}
