package io.github.adoct.parser.model;

public record LinksAttachment(String filename) implements LinksValue {

  @Override
  public String label() {
    return filename;
  }
}
