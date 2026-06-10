package ru.gitverse.adoct.parser.macros;

import lombok.SneakyThrows;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import ru.gitverse.adoct.MetadataKey;
import ru.gitverse.adoct.parser.doc.ParseContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public class MacrosCode extends AbstractParseMacros {
    int index = 1;

    public MacrosCode() {
        super("code");
    }


    @SneakyThrows
    @Override
    public void parse(String name, Map<String, String> parameter, Element body, ParseContext parseContext) {
        printer.println();

        String text = body.text();
        String title = parameter.get("title");
        printer.println(".%s".formatted(title));
        String language = parameter.get("language");
        if (language == null) {
            if (StringUtils.startsWith(text, "{")) {
                language = "json";
            } else if (StringUtils.startsWith(text, "<")) {
                language = "xml";
            }
        }
        if (language == null) {
            printer.println("[source]");
        } else {
            printer.println("[source, %s]".formatted(language));
        }
        printer.println("----");
        if (StringUtils.countMatches(text, "\n") > parseContext.getMaxIncludeString()) {
            Path filesFolder = getFilesFolder(parseContext);
            String fileName = makeFileName(title, language);
            Files.writeString(filesFolder.resolve(fileName), text);
            printer.println("include::%s[]".formatted(getNameFiles(parseContext, fileName)));
        } else {
            printer.println(text);
        }
        printer.println("----");
    }


    private String getNameFiles(ParseContext parseContext, String fileName) {
        return Optional.ofNullable(parseContext.getMetadata().get(MetadataKey.FILES_FOLDER_NAME))
                .map(String.class::cast)
                .map(it -> it + "/" + fileName)
                .orElse(fileName);
    }

    private String makeFileName(String title, String language) {
        String filename;
        if (title == null || "null".equals(title)) {
            filename = "include_file_%d".formatted(index++);
        } else {
            filename = FilenameUtils.normalize(title) + "_%d".formatted(index++);
        }
        if (language != null) {
            return filename + "." + language;
        } else {
            return filename;
        }
    }

    private static Path getFilesFolder(ParseContext parseContext) {
        return (Path) parseContext.getMetadata().getOrDefault(MetadataKey.FILES_FOLDER,
                parseContext.getMetadata().get(MetadataKey.DESTINATION_FOLDER)
        );
    }
}