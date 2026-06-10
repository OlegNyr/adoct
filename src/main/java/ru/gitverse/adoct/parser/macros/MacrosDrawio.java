
package ru.gitverse.adoct.parser.macros;

import ar.com.hjg.pngj.IImageLine;
import ar.com.hjg.pngj.PngReader;
import ar.com.hjg.pngj.PngWriter;
import ar.com.hjg.pngj.chunks.PngChunkTextVar;
import lombok.SneakyThrows;
import org.jsoup.nodes.Element;
import ru.gitverse.adoct.MetadataKey;
import ru.gitverse.adoct.parser.doc.ParseContext;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class MacrosDrawio extends AbstractParseMacros {
    public MacrosDrawio() {
        super("drawio", "inc-drawio");
    }

    @SneakyThrows
    @Override
    public void parse(String name, Map<String, String> parameter, Element body, ParseContext parseContext) {
        String filename = parameter.get("diagramName");
        Path attachFolder = (Path) parseContext.getMetadata().get(MetadataKey.ATTACH_FOLDER);
        String destFilePng = filename + ".drawio.png";
        Path destinationDrawioPng = attachFolder.resolve(destFilePng);
        if (Files.notExists(destinationDrawioPng)) {

            Path pngFile = attachFolder.resolve(filename + ".png");
            if (Files.exists(pngFile)) {
                convertDrawIoPng(pngFile,
                        attachFolder.resolve(filename),
                        destinationDrawioPng
                );
            } else {
                printer.println("Diagram attachment access error: cannot display diagram");
                return;
            }
        }
        printer.println();
        String attachFolderName = (String) parseContext.getMetadata().get(MetadataKey.ATTACH_FOLDER_NAME);
        printer.println("image::%s/%s[]".formatted(attachFolderName, destFilePng));
        printer.println();
    }


    private static void convertDrawIoPng(Path sourcePng, Path drawIoFile, Path destinationPng) throws IOException {
        if (Files.notExists(sourcePng)) {
            throw new RuntimeException("not found " + sourcePng);
        }
        if (Files.notExists(drawIoFile)) {
            throw new RuntimeException("not found " + drawIoFile);
        }

        String xmlFileBody = Files.readString(drawIoFile);
        String drawIoEncode = encodeUrl(xmlFileBody);

        PngReader pngReader = new PngReader(sourcePng.toFile());
        PngWriter pngWriter = new PngWriter(destinationPng.toFile(), pngReader.imgInfo, true);
        try {
            pngWriter.copyChunksFrom(pngReader.getChunksList());
            PngChunkTextVar textVar = pngWriter.getMetadata().setText("mxfile", drawIoEncode, true, false);
            textVar.setPriority(true);

            for (int i = 0; i < pngReader.imgInfo.rows; i++) {
                IImageLine iImageLine = pngReader.readRow(i);
                pngWriter.writeRow(iImageLine);
            }
            pngReader.end();
        } finally {
            pngWriter.end();
        }
        //Files.move(sourcePng, sourcePng.resolve(".old"));
        //Files.move(drawIoFile, drawIoFile.resolve(".old"));
    }

    private static String encodeUrl(String xmlFileBody) {
        return URLEncoder.encode(xmlFileBody, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
