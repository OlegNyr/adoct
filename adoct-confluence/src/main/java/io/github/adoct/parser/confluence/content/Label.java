package io.github.adoct.parser.confluence.content;

import lombok.Data;

/** Метка страницы Confluence. Имя приходит в {@code name}, в старых версиях — в {@code label}. */
@Data
public class Label {

    public String name;
    public String label;

    /** Имя метки: {@code name}, иначе {@code label}; пусто, если не задано ни то ни другое. */
    public String displayName() {
        if (name != null && !name.isBlank()) {
            return name;
        }
        return label == null ? "" : label;
    }
}
