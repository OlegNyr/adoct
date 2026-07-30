package io.github.adoct.parser.model;

public record LinksUser(String userKey) implements LinksValue {

  @Override
  public String label() {
    return userKey;
  }
}
