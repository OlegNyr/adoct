package ru.gitverse.adoct.parser.build.macro;

import ar.com.hjg.pngj.IImageLine;
import ar.com.hjg.pngj.PngReader;
import ar.com.hjg.pngj.PngWriter;
import ar.com.hjg.pngj.chunks.PngChunkTextVar;
import lombok.SneakyThrows;
import ru.gitverse.adoct.parser.model.MetadataKey;
import ru.gitverse.adoct.parser.ast.Block;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Вшивает drawio-XML в чанк {@code mxfile} PNG-вложения и отдаёт блок {@code image::}.
 * Логика перенесена из {@code MacrosDrawio}.
 */
final class DrawioRenderer {

    private DrawioRenderer() {
    }

    @SneakyThrows
    static Block render(String diagramName, Map<MetadataKey, Object> metadata) {
        Path attachFolder = (Path) metadata.get(MetadataKey.ATTACH_FOLDER);
        String destFilePng = diagramName + ".drawio.png";
        Path destinationDrawioPng = attachFolder.resolve(destFilePng);
        if (Files.notExists(destinationDrawioPng)) {
            Path pngFile = attachFolder.resolve(diagramName + ".png");
            if (Files.exists(pngFile)) {
                convertDrawIoPng(pngFile, attachFolder.resolve(diagramName), destinationDrawioPng);
            } else {
                return new Block.RawBlock("Diagram attachment access error: cannot display diagram");
            }
        }
        String attachFolderName = (String) metadata.get(MetadataKey.ATTACH_FOLDER_NAME);
        return new Block.RawBlock("image::%s/%s[]".formatted(attachFolderName, destFilePng));
    }

    private static void convertDrawIoPng(Path sourcePng, Path drawIoFile, Path destinationPng) throws IOException {
        if (Files.notExists(sourcePng)) {
            throw new RuntimeException("not found " + sourcePng);
        }
        if (Files.notExists(drawIoFile)) {
            throw new RuntimeException("not found " + drawIoFile);
        }
        String drawIoEncode = encodeUrl(Files.readString(drawIoFile));

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
    }

    private static String encodeUrl(String xmlFileBody) {
        return URLEncoder.encode(xmlFileBody, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
