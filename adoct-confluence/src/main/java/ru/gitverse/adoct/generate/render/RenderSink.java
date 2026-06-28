package ru.gitverse.adoct.generate.render;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Приёмник результата рендеринга: накапливает тело страницы (XHTML) и список локальных файлов
 * (картинки и ссылки на файлы), которые нужно загрузить как вложения. Заменяет сквозную передачу
 * пары {@code (StringBuilder, List<Path>)} по дереву обхода.
 */
final class RenderSink {

    private final StringBuilder xhtml = new StringBuilder();
    private final List<Path> attachments = new ArrayList<>();

    RenderSink append(String text) {
        xhtml.append(text);
        return this;
    }

    RenderSink append(char c) {
        xhtml.append(c);
        return this;
    }

    RenderSink append(int value) {
        xhtml.append(value);
        return this;
    }

    /** Список вложений — мутабельный, чтобы {@link InlineNormalizer} мог дописывать найденные файлы. */
    List<Path> attachments() {
        return attachments;
    }

    String xhtml() {
        return xhtml.toString();
    }
}
