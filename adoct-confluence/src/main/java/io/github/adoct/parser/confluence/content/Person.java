package io.github.adoct.parser.confluence.content;

import lombok.Data;

/** Пользователь Confluence в метаданных страницы (нужен только {@code displayName}). */
@Data
public class Person {
    String displayName;
}
