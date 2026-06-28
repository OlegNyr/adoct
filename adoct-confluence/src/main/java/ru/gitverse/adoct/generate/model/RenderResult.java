package ru.gitverse.adoct.generate.model;

import java.nio.file.Path;
import java.util.List;

/**
 * Результат рендеринга AsciiDoc-документа в Confluence storage format.
 *
 * @param xhtml  тело страницы в storage format (XHTML)
 * @param images локальные файлы картинок, на которые ссылается страница и которые
 *               нужно загрузить как вложения перед обновлением страницы
 */
public record RenderResult(String xhtml, List<Path> images) {
}
