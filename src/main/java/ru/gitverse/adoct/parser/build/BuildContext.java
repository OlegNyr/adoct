package ru.gitverse.adoct.parser.build;

import ru.gitverse.adoct.parser.model.MetadataKey;

import java.util.Map;

/**
 * Неизменяемое состояние обхода для построения AST (замена {@code ParseContext} в новом пути).
 * <p>
 * Уровень заголовка и глубина списков/таблиц передаются явно по стеку вызовов — без мутабельного
 * {@code topHeader} и состояния writer'а.
 *
 * @param metadata         метаданные конвертации (ссылки, папки, флаги)
 * @param workColor        экспортировать ли цвет текста
 * @param maxIncludeString порог строк, после которого код/plantuml выносится во внешний файл
 * @param listDepth        текущая глубина вложенности списка (0 — вне списка)
 * @param headingLevel     уровень последнего заголовка (для под-заголовков expand/tabs)
 * @param tableDepth       глубина вложенности таблиц (0 — верхний уровень)
 */
public record BuildContext(Map<MetadataKey, Object> metadata,
                           boolean workColor,
                           int maxIncludeString,
                           int listDepth,
                           int headingLevel,
                           int tableDepth) {

    public static BuildContext root(Map<MetadataKey, Object> metadata, boolean workColor) {
        return new BuildContext(metadata, workColor, 10, 0, 0, 0);
    }

    public BuildContext withListDepth(int depth) {
        return new BuildContext(metadata, workColor, maxIncludeString, depth, headingLevel, tableDepth);
    }

    public BuildContext withHeadingLevel(int level) {
        return new BuildContext(metadata, workColor, maxIncludeString, listDepth, level, tableDepth);
    }

    public BuildContext withTableDepth(int depth) {
        return new BuildContext(metadata, workColor, maxIncludeString, listDepth, headingLevel, depth);
    }
}
