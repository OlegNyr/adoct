package io.github.adoct.parser.confluence.content;

import lombok.Data;

/** История страницы ({@code expand=history}): дата создания и автор. */
@Data
public class History {
    String createdDate;
    Person createdBy;
}
