package ru.gitverse.adoct.bugreport;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

/**
 * Содержимое баг-репорта о неудачной конвертации (экспорт Confluence → AsciiDoc или импорт обратно).
 *
 * <p>{@link #render()} формирует человекочитаемый {@code report.txt}: операция, время, версия плагина,
 * окружение, обезличенный контекст ({@code url}, имя источника) и полный стек ошибки. Сами входные
 * данные кладутся рядом отдельно — уже анонимизированными (см. {@link BugReportWriter}).
 */
public record BugReport(String operation,
                        String timestamp,
                        String pluginVersion,
                        Map<String, String> context,
                        Throwable error) {

    public String render() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("AsciiDocTools bug report\n");
        sb.append("========================\n\n");
        sb.append("operation : ").append(operation).append('\n');
        sb.append("timestamp : ").append(timestamp).append('\n');
        sb.append("plugin    : ").append(pluginVersion).append('\n');
        sb.append("java      : ").append(System.getProperty("java.version")).append('\n');
        sb.append("os        : ").append(System.getProperty("os.name"))
                .append(' ').append(System.getProperty("os.version")).append('\n');

        sb.append('\n').append("context:\n");
        if (context != null && !context.isEmpty()) {
            context.forEach((key, value) -> sb.append("  ").append(key).append(" = ").append(value).append('\n'));
        } else {
            sb.append("  (none)\n");
        }

        sb.append('\n').append("error: ").append(error == null ? "(none)" : String.valueOf(error.getMessage()));
        sb.append('\n').append('\n').append("stacktrace:\n").append(stackTrace());
        return sb.toString();
    }

    public String stackTrace() {
        if (error == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
