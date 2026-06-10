package ru.gitverse.adoct;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.output.TeeWriter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import ru.gitverse.adoct.parser.PrintWriterReturn;
import ru.gitverse.adoct.parser.doc.ParseContext;
import ru.gitverse.adoct.parser.doc.ParseDispatcher;
import ru.gitverse.adoct.parser.doc.ParseHeader;
import ru.gitverse.adoct.parser.doc.ParseImg;
import ru.gitverse.adoct.parser.doc.ParseList;
import ru.gitverse.adoct.parser.doc.ParseParagraph;
import ru.gitverse.adoct.parser.doc.ParseSection;
import ru.gitverse.adoct.parser.doc.ParseTable;
import ru.gitverse.adoct.parser.doc.ParseTags;

import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.nio.charset.StandardCharsets.UTF_8;


@Slf4j
public class ConvertDocsToAdocZip {

    @SneakyThrows
    public Path convert(Path source, Path destination) {
        if (!Files.exists(source) || !Files.isReadable(source)) {
            throw new IllegalArgumentException("Файл источник не существует %s".formatted(source));
        }

        if (!Files.exists(destination) || !Files.isDirectory(destination)) {
            throw new IllegalArgumentException("Папка назначения не существует %s".formatted(destination));
        }
        log.info("Обрабатываем файл {}", source);
        Path tempDirectory = Files.createTempDirectory("convertdoc" + System.currentTimeMillis());
        extractAllFiles(source, tempDirectory);


        Path endPointFileSource = EndpointFile.getEndpointFile(tempDirectory);

        String title = HeaderText.getHeader(endPointFileSource);

        Path endPointFileNameDestinationAdoc = Optional.of(subPath(endPointFileSource, tempDirectory))
                .map(destination::resolve)
                .map(p -> changeExtension(p, "adoc"))
                .orElseThrow(() -> new RuntimeException("Не смогли формиировать путь к файлу"));
        Document document = Jsoup.parse(endPointFileSource, UTF_8.name());

        try (Writer writerOut = new FileWriter(endPointFileNameDestinationAdoc.toFile(), UTF_8);
             TeeWriter writer = new TeeWriter(writerOut, new OutputStreamWriter(System.out, UTF_8));
             PrintWriterReturn print = new PrintWriterReturn(writer, true)) {


            print.println(":imagesdir: ./image");
            print.println();
            print.println("= %s".formatted(title));

            Element mainContent = document.getElementById("main-content");
            if (mainContent == null) {
                throw new RuntimeException("Не найден \"main-content\" в файле %s".formatted(endPointFileSource));
            }

            List<ParseTags> parseTagsList = List.of(
                    new ParseImg(tempDirectory, destination.resolve("image")),
                    new ParseParagraph(),
                    new ParseTable(),
                    new ParseSection(),
                    new ParseHeader(),
                    new ParseList()
            );

            ParseDispatcher dispatcher = new ParseDispatcher(print, parseTagsList);

            dispatcher.parse(mainContent, ParseContext.EMPTY);
        }
        return endPointFileNameDestinationAdoc;

    }

    private Path changeExtension(Path path, String extension) {
        String fileName = path.toString();
        int lastIndexOf = fileName.lastIndexOf('.');
        if (lastIndexOf == -1) {
            return Path.of(fileName + "." + extension);
        } else {
            return Path.of(fileName.substring(0, lastIndexOf) + "." + extension);
        }

    }

    private Path subPath(Path endPointFileSource, Path directrory) {
        if (!endPointFileSource.startsWith(directrory)) {
            throw new RuntimeException("Не правильно задан временный каталог");
        }

        return endPointFileSource.subpath(directrory.getNameCount(), endPointFileSource.getNameCount());
    }

    private static void extractAllFiles(Path source, Path destDir) throws IOException {
        try (ZipFile zipFile = new ZipFile(source.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                Path resolverPath = destDir.resolve(ze.getName()).normalize();
                if (!resolverPath.startsWith(destDir)) {
                    throw new RuntimeException("Entry with an illeteral path " + ze.getName());
                }
                log.debug("Extract files {}", resolverPath);
                if (ze.isDirectory()) {
                    Files.createDirectories(resolverPath);
                } else {
                    Files.createDirectories(resolverPath.getParent());
                    Files.copy(zipFile.getInputStream(ze), resolverPath);
                }
            }
        }
        log.info("Source directory {}", destDir);
    }

}
