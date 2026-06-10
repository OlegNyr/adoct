package ru.gitverse.adoct.generate.confluence;

/**
 * Минимальные данные о существующей странице, нужные для её обновления.
 *
 * @param title  текущий заголовок (Confluence требует передавать его при обновлении)
 * @param number текущий номер версии (новая версия = number + 1)
 */
public record PageVersion(String title, int number) {
}
