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

public class MacrosPlantuml extends AbstractParseMacros {
    int index = 1;

    public MacrosPlantuml() {
        super("plantuml");
    }

    @SneakyThrows
    @Override
    public void parse(String name, Map<String, String> parameter, Element body, ParseContext parseContext) {
        printer.println();
        String title = parameter.get("title");
        if (StringUtils.isNotEmpty(title)) {
            printer.println(".%s".formatted(title));
        }
        printer.println("[plantuml, format=\"png\"]");
        printer.println("----");

        String text = body.text();
        if (StringUtils.countMatches(text, "\n") > parseContext.getMaxIncludeString()) {
            Path filesFolder = getFilesFolder(parseContext);
            String fileName = makeFileName(title);
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

    private String makeFileName(String title) {
        if (title == null || "null".equals(title)) {
            return "plantuml_%d.puml".formatted(index++);
        } else {
            return FilenameUtils.normalize(title) + "_%d.puml".formatted(index++);
        }
    }

    private static Path getFilesFolder(ParseContext parseContext) {
        return (Path) parseContext.getMetadata().getOrDefault(MetadataKey.FILES_FOLDER,
                parseContext.getMetadata().get(MetadataKey.DESTINATION_FOLDER)
        );
    }
}