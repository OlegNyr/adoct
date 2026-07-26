package io.github.adoct.parser;

import io.github.adoct.parser.ast.AsciiDocWriter;
import io.github.adoct.parser.ast.BlockWriter;
import io.github.adoct.parser.ast.MarkdownWriter;

import java.util.Locale;

/**
 * Целевой формат экспорта: AsciiDoc или Markdown (GFM). Держит формат-специфику точки записи —
 * имя выходного файла, маркер заголовка для сплиттера и создание нужного {@link BlockWriter}.
 */
public enum OutputFormat {

    /** AsciiDoc — исходный формат экспорта. */
    ADOC("adoc", "index.adoc", "==") {
        @Override
        public BlockWriter writer(String imagesDir) {
            return new AsciiDocWriter();
        }
    },
    /** Markdown (GitHub Flavored). */
    MD("md", "index.md", "##") {
        @Override
        public BlockWriter writer(String imagesDir) {
            return new MarkdownWriter(imagesDir);
        }
    };

    private final String extension;
    private final String indexFileName;
    private final String splitHeading;

    OutputFormat(String extension, String indexFileName, String splitHeading) {
        this.extension = extension;
        this.indexFileName = indexFileName;
        this.splitHeading = splitHeading;
    }

    /** Расширение файлов ({@code adoc}/{@code md}). */
    public String extension() {
        return extension;
    }

    /** Имя корневого файла страницы ({@code index.adoc}/{@code index.md}). */
    public String indexFileName() {
        return indexFileName;
    }

    /** Префикс заголовка уровня 2, по которому сплиттер режет большой документ на файлы. */
    public String splitHeading() {
        return splitHeading;
    }

    /** Создаёт writer этого формата; {@code imagesDir} — папка картинок для относительных путей (Markdown). */
    public abstract BlockWriter writer(String imagesDir);

    /** Разбор из строки (регистронезависимо); {@code null}/неизвестное → {@link #ADOC}. */
    public static OutputFormat from(String value) {
        if (value == null) {
            return ADOC;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "md", "markdown" -> MD;
            default -> ADOC;
        };
    }
}
