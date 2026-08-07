package io.github.adoct.plugins.idea.service;

import io.github.adoct.parser.OutputFormat;

/**
 * Настройки одной выгрузки из Confluence — то, что пользователь выставил в панели экспорта.
 *
 * <p>Собраны в record осознанно: позиционных параметров у {@code convert} накопилось больше десятка,
 * причём подряд идут несколько {@code boolean} — перепутать их местами было бы легко, а компилятор
 * этого не заметил бы.
 *
 * @param exportColors       переносить ли исходные цвета текста
 * @param debug              оставлять ли папку {@code source/} с сырыми ответами Confluence
 * @param includeChildren    выгружать ли поддерево дочерних страниц
 * @param includeAttachments скачивать ли вложения
 * @param onlyUsedAttachments качать только вложения, на которые ссылается тело страницы
 * @param format             целевой формат ({@code null} — AsciiDoc)
 * @param skipUnchanged      пропускать ли страницы, версия которых совпадает с уже выгруженной
 * @param exportSpace        выгружать ли пространство целиком (URL — ссылка на пространство)
 * @param exportThreads      сколько страниц выгружать одновременно; {@code 1} — последовательно
 */
public record ExportOptions(boolean exportColors,
                            boolean debug,
                            boolean includeChildren,
                            boolean includeAttachments,
                            boolean onlyUsedAttachments,
                            OutputFormat format,
                            boolean skipUnchanged,
                            boolean exportSpace,
                            int exportThreads) {

    /** Минимум и максимум степени параллелизма, предлагаемые в UI. */
    public static final int MIN_THREADS = 1;
    public static final int MAX_THREADS = 16;
    /** Степень параллелизма по умолчанию — совпадает с дефолтом движка. */
    public static final int DEFAULT_THREADS = 4;

    public ExportOptions {
        format = format == null ? OutputFormat.ADOC : format;
        exportThreads = clampThreads(exportThreads);
    }

    /** Приводит степень параллелизма в допустимый диапазон (значение приходит из сохранённого состояния). */
    public static int clampThreads(int value) {
        return Math.min(Math.max(value, MIN_THREADS), MAX_THREADS);
    }
}
