package ru.gitverse.adoct.generate.asciidoc;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.asciidoctor.ast.Document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Разбирает {@code .adoc}-файл в AST AsciiDoctor ({@link Document}) без конвертации в HTML.
 * Дальше по дереву ходит {@link ru.gitverse.adoct.generate.render.StorageRenderer}.
 */
public final class AsciiDocParser implements AutoCloseable {

    private final Asciidoctor asciidoctor = Asciidoctor.Factory.create();

    public AsciiDocParser() {
        // include::*.adoc[] → макрос Confluence «Include Page» вместо инлайнинга содержимого.
        asciidoctor.javaExtensionRegistry().includeProcessor(new ConfluenceIncludeProcessor());
    }

    /**
     * Загружает документ. Базовая директория устанавливается в папку файла, чтобы
     * относительные пути картинок (и атрибут {@code imagesdir}) разрешались корректно.
     */
    public Document parse(Path adocFile) throws IOException {
        String text = Files.readString(adocFile, StandardCharsets.UTF_8);
        Path baseDir = adocFile.toAbsolutePath().getParent();
        Options options = Options.builder()
                .safe(SafeMode.UNSAFE)
                .baseDir(baseDir.toFile())
                // Межфайловые ссылки <<file.adoc#id,..>> должны сохранять расширение .adoc
                // (по умолчанию AsciiDoctor переписывает его в .html), чтобы рендерер опознал цель.
                .attributes(Attributes.builder().attribute("outfilesuffix", ".adoc").build())
                .build();
        return asciidoctor.load(text, options);
    }

    @Override
    public void close() {
        asciidoctor.close();
    }
}
