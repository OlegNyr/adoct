package ru.gitverse.adoct;

import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

@Ignore("Ручной черновик: хардкод абсолютных путей D:\\AsciiDocTools и удалённые фикстуры. Запускать вручную.")
public class ConvertStorageToAdocTest {
    @Test
    public void startConvert() throws IOException {
        String content = Files.readString(Path.of("D:\\AsciiDocTools\\src\\test\\resources\\content.html"));
        Path destination = Path.of("D:\\AsciiDocTools\\build\\asciidoc");

        if (Files.exists(destination)) {
            Files.walk(destination).sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        Files.createDirectory(destination);
        ConvertStorageToAdoc convert = new ConvertStorageToAdoc(content, null, destination);
        convert.convert(Map.of(),
                Path.of("D:\\AsciiDocTools\\src\\test\\resources\\attache")
        );


    }

    @Test
    public void getLinks() throws IOException {
        String content = Files.readString(Path.of("D:\\AsciiDocTools\\src\\test\\resources\\content.html"));
        ConvertStorageToAdoc convert = new ConvertStorageToAdoc(content, null, Path.of(""));

        for (LinksValue link : convert.getLinks()) {
            System.out.println(link);
        }


    }
}