package ru.gitverse.adoct.parser.build.macro;

import ar.com.hjg.pngj.IImageLine;
import ar.com.hjg.pngj.PngReader;
import ar.com.hjg.pngj.PngWriter;
import ar.com.hjg.pngj.chunks.PngChunkTextVar;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import ru.gitverse.adoct.parser.ast.Block;
import ru.gitverse.adoct.parser.model.MetadataKey;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Готовит редактируемый drawio-файл из вложений Confluence и отдаёт блок {@code image::}.
 *
 * <p>Источником считается то, что выгрузил drawio-плагин Confluence. Поддерживаются варианты
 * (сохраняя исходный формат — PNG остаётся PNG, SVG остаётся SVG):
 * <ol>
 *   <li>рядом уже лежит готовый {@code <имя>.drawio.png} / {@code <имя>.drawio.svg} → ссылаемся как есть;</li>
 *   <li>сырой drawio-XML (файл {@code <имя>} без расширения) + рендер {@code <имя>.png}/{@code <имя>.svg} →
 *       вшиваем XML в картинку (в чанк {@code mxfile} для PNG, в атрибут {@code content} для SVG);</li>
 *   <li>сам аттач {@code <имя>} уже является комбинированным drawio-файлом (PNG с чанком {@code mxfile}
 *       или SVG с атрибутом {@code content}) → даём ему расширение {@code .drawio.png}/{@code .drawio.svg}.</li>
 * </ol>
 */
@Slf4j
final class DrawioRenderer {

    /** Открывающий тег корневого {@code <svg ...>} (до первого {@code >}). */
    private static final Pattern SVG_OPEN = Pattern.compile("<svg\\b[^>]*", Pattern.CASE_INSENSITIVE);
    /** Уже присутствующий атрибут {@code content="..."} в открывающем теге svg. */
    private static final Pattern SVG_CONTENT_ATTR = Pattern.compile("\\s+content\\s*=\\s*\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE);

    private DrawioRenderer() {
    }

    static Block render(String diagramName, Map<MetadataKey, Object> metadata) {
        Path attachFolder = (Path) metadata.get(MetadataKey.ATTACH_FOLDER);
        String attachFolderName = (String) metadata.get(MetadataKey.ATTACH_FOLDER_NAME);

        String fileName = prepareEditable(diagramName, attachFolder);
        if (fileName == null) {
            return new Block.RawBlock("Diagram attachment access error: cannot display diagram");
        }
        return new Block.RawBlock("image::%s/%s[]".formatted(attachFolderName, fileName));
    }

    /**
     * Готовит редактируемый файл в каталоге вложений и возвращает его имя (или {@code null}, если
     * собрать не из чего).
     */
    @SneakyThrows
    private static String prepareEditable(String diagramName, Path attachFolder) {
        // 1. Готовый комбинированный файл уже на месте — сохраняем формат, ничего не пересобираем.
        for (String ext : new String[] {".drawio.png", ".drawio.svg"}) {
            String ready = diagramName + ext;
            if (Files.exists(attachFolder.resolve(ready))) {
                return ready;
            }
        }

        Path rawXml = attachFolder.resolve(diagramName);
        Path png = attachFolder.resolve(diagramName + ".png");
        Path svg = attachFolder.resolve(diagramName + ".svg");

        // 2. Сырой drawio-XML + рендер → вшиваем XML в картинку.
        if (hasDrawioXml(rawXml)) {
            String drawioXml = Files.readString(rawXml, StandardCharsets.UTF_8);
            if (Files.exists(png)) {
                String dest = diagramName + ".drawio.png";
                embedIntoPng(png, drawioXml, attachFolder.resolve(dest));
                return dest;
            }
            if (Files.exists(svg)) {
                String dest = diagramName + ".drawio.svg";
                embedIntoSvg(svg, drawioXml, attachFolder.resolve(dest));
                return dest;
            }
        }

        // 3. Сам аттач/рендер уже несёт встроенный drawio — даём ему правильное расширение.
        if (pngCarriesDrawio(rawXml) || pngCarriesDrawio(png)) {
            Path source = pngCarriesDrawio(rawXml) ? rawXml : png;
            String dest = diagramName + ".drawio.png";
            Files.copy(source, attachFolder.resolve(dest), StandardCopyOption.REPLACE_EXISTING);
            return dest;
        }
        if (svgCarriesDrawio(rawXml) || svgCarriesDrawio(svg)) {
            Path source = svgCarriesDrawio(rawXml) ? rawXml : svg;
            String dest = diagramName + ".drawio.svg";
            Files.copy(source, attachFolder.resolve(dest), StandardCopyOption.REPLACE_EXISTING);
            return dest;
        }

        log.warn("Не нашёл вложений для drawio-диаграммы {}", diagramName);
        return null;
    }

    /** Вшивает drawio-XML в чанк {@code mxfile} PNG. */
    private static void embedIntoPng(Path sourcePng, String drawioXml, Path destinationPng) throws IOException {
        String drawIoEncode = encodeUrl(drawioXml);
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

    /** Вшивает drawio-XML в атрибут {@code content} корневого {@code <svg>} (формат drawio-SVG). */
    private static void embedIntoSvg(Path sourceSvg, String drawioXml, Path destinationSvg) throws IOException {
        String svg = Files.readString(sourceSvg, StandardCharsets.UTF_8);
        Matcher open = SVG_OPEN.matcher(svg);
        if (!open.find()) {
            throw new RuntimeException("not an svg: " + sourceSvg);
        }
        String openTag = SVG_CONTENT_ATTR.matcher(open.group()).replaceAll("");
        String withContent = openTag + " content=\"" + escapeXmlAttr(drawioXml) + "\"";
        String result = svg.substring(0, open.start()) + withContent + svg.substring(open.end());
        Files.writeString(destinationSvg, result, StandardCharsets.UTF_8);
    }

    /** Файл существует и в начале содержит drawio-XML ({@code <mxfile}/{@code mxGraphModel}). */
    private static boolean hasDrawioXml(Path file) throws IOException {
        if (Files.notExists(file)) {
            return false;
        }
        String head = head(file, 512);
        return head.contains("<mxfile") || head.contains("mxGraphModel");
    }

    /** PNG со встроенным drawio-чанком {@code mxfile}. */
    private static boolean pngCarriesDrawio(Path file) {
        if (Files.notExists(file) || !isPng(file)) {
            return false;
        }
        try {
            PngReader reader = new PngReader(file.toFile());
            try {
                return !reader.getMetadata().getTxtForKey("mxfile").isEmpty();
            } finally {
                reader.end();
            }
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** SVG со встроенной диаграммой (атрибут {@code content="...mxfile..."}). */
    private static boolean svgCarriesDrawio(Path file) {
        try {
            if (Files.notExists(file) || isPng(file)) {
                return false;
            }
            String text = Files.readString(file, StandardCharsets.UTF_8);
            return text.contains("<svg") && text.contains("content=")
                    && (text.contains("mxfile") || text.contains("mxGraphModel"));
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isPng(Path file) {
        try {
            byte[] sig = head(file, 8).getBytes(StandardCharsets.ISO_8859_1);
            return sig.length >= 4 && (sig[0] & 0xFF) == 0x89 && sig[1] == 0x50 && sig[2] == 0x4E && sig[3] == 0x47;
        } catch (IOException e) {
            return false;
        }
    }

    private static String head(Path file, int bytes) throws IOException {
        byte[] all = Files.readAllBytes(file);
        return new String(all, 0, Math.min(all.length, bytes), StandardCharsets.ISO_8859_1);
    }

    private static String encodeUrl(String xmlFileBody) {
        return URLEncoder.encode(xmlFileBody, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String escapeXmlAttr(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
