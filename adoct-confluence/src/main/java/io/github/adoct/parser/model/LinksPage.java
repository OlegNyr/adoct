package io.github.adoct.parser.model;

public record LinksPage(String title, String space) implements LinksValue {

  @Override
  public String label() {
    return title;
  }
}
