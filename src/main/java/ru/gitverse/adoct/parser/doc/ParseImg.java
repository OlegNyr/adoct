package ru.gitverse.adoct.parser.doc;

import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import ru.gitverse.adoct.parser.PrintWriterReturn;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Slf4j
public class ParseImg implements ParseTags, SetterDispatcher {
    private final Path source;
    private final Path destination;
    @Setter
    private PrintWriterReturn printWriter;
    @Setter
    private ParseDispatcher dispatcher;

    public ParseImg(Path source, Path destination) {
        this.source = source;
        this.destination = destination;
    }


    @Override
    public List<String> tags() {
        return List.of("img");
    }

    @SneakyThrows
    @Override
    public void parse(Node node, ParseContext parseContext) {
        if (!printWriter.isLastReturn()) {
            printWriter.print(" ");
            printWriter.print("image:");
        } else {
            printWriter.println();
            printWriter.print("image::");
        }

        String filenameRaw = node.attr("src");
        String filename = Path.of(filenameRaw).getFileName().toString();
        printWriter.print(filename);
        printWriter.print("[");

        if (node instanceof Element el) {
            if (el.children().isEmpty()) {
                printWriter.print(el.text());
            } else {
                dispatcher.parse(el.children(), parseContext.addOption(DispatherOption.PARSE_TRIM));
            }
        } else {
            printWriter.print(ParseTags.getNodeText(node));
        }
        printWriter.print("]");

        Path sourceFile = source.resolve(filenameRaw);
        if (Files.exists(sourceFile)) {
            Path target = destination.resolve(filename);
            Files.createDirectories(target.getParent());
            Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);
        } else {
            log.warn("Not found file {}", sourceFile);
        }
    }
}
