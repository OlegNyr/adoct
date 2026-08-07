package io.github.adoct.parser.confluence.content;

import lombok.Data;

import java.util.List;

/** Метки страницы из {@code expand=metadata.labels}: обёртка над списком {@link Label}. */
@Data
public class Labels {

    public List<Label> results;
}
