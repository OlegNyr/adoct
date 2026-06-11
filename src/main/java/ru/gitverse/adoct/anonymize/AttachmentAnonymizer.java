package ru.gitverse.adoct.anonymize;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Анонимизация каталога вложений.
 *
 * <p>Картинки заменяются плейсхолдером того же размера (убирает скриншоты с рабочими
 * данными); встроенный/отдельный drawio-XML вычищается (подписи диаграмм заменяются);
 * прочие файлы заменяются короткой заглушкой. Имена файлов переименовываются согласованно
 * через {@link Anonymizer#fileName(String)}.
 */
@Slf4j
public class AttachmentAnonymizer {

    private static final Pattern DRAWIO_LABEL = Pattern.compile("(value|label)=\"([^\"]*)\"");

    private final Anonymizer anon;

    public AttachmentAnonymizer(Anonymizer anon) {
        this.anon = anon;
    }

    @SneakyThrows
    public void process(Path sourceDir, Path destinationDir) {
        if (!Files.isDirectory(sourceDir)) {
            return;
        }
        Files.createDirectories(destinationDir);
        try (Stream<Path> files = Files.list(sourceDir)) {
            files.filter(Files::isRegularFile).forEach(file -> processFile(file, destinationDir));
        }
        // во вторую очередь — каталоги (на случай вложенности)
        try (Stream<Path> dirs = Files.list(sourceDir)) {
            dirs.filter(Files::isDirectory).forEach(dir ->
                    process(dir, destinationDir.resolve(anon.fileName(dir.getFileName().toString()))));
        }
    }

    @SneakyThrows
    private void processFile(Path file, Path destinationDir) {
        anonymizeContentTo(file, destinationDir.resolve(anon.fileName(file.getFileName().toString())));
    }

    /**
     * Анонимизирует содержимое одного файла в заданный {@code target} (имя берётся как есть, без
     * переименования — чтобы ссылки на вложение из {@code .adoc} продолжали резолвиться при
     * воспроизведении). Картинка → плейсхолдер того же размера, drawio-XML → подписи вычищаются,
     * прочее → короткая заглушка.
     */
    @SneakyThrows
    public void anonymizeContentTo(Path file, Path target) {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        String originalName = file.getFileName().toString();
        byte[] bytes = Files.readAllBytes(file);
        if (isImage(originalName, bytes)) {
            writePlaceholderImage(bytes, target);
        } else if (isDrawioXml(bytes)) {
            Files.writeString(target, scrubDrawio(new String(bytes, StandardCharsets.UTF_8)));
        } else {
            Files.writeString(target, "anonymized");
        }
    }

    private void writePlaceholderImage(byte[] original, Path target) {
        int width = 640;
        int height = 400;
        try {
            BufferedImage read = ImageIO.read(new ByteArrayInputStream(original));
            if (read != null) {
                width = read.getWidth();
                height = read.getHeight();
            }
        } catch (Exception e) {
            log.warn("Cannot read image size for {}, using default", target, e);
        }
        BufferedImage image = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(0xEE, 0xEE, 0xEE));
            g.fillRect(0, 0, width, height);
            g.setColor(new Color(0xBB, 0xBB, 0xBB));
            g.setStroke(new BasicStroke(2));
            g.drawRect(1, 1, width - 3, height - 3);
            g.drawLine(0, 0, width, height);
            g.drawLine(0, height, width, 0);
            g.setColor(new Color(0x55, 0x55, 0x55));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
            String label = "%d x %d".formatted(width, height);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(label, (width - fm.stringWidth(label)) / 2, height / 2);
        } finally {
            g.dispose();
        }
        try {
            ImageIO.write(image, "png", target.toFile());
        } catch (Exception e) {
            log.warn("Cannot write placeholder image {}", target, e);
        }
    }

    private String scrubDrawio(String xml) {
        Matcher matcher = DRAWIO_LABEL.matcher(xml);
        StringBuilder sb = new StringBuilder(xml.length());
        while (matcher.find()) {
            String attr = matcher.group(1);
            String value = matcher.group(2);
            String replaced = StringUtils.isBlank(value) ? value : anon.inlineText(value);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(attr + "=\"" + replaced + "\""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static boolean isImage(String name, byte[] bytes) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".bmp")) {
            return true;
        }
        // PNG magic
        return bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47;
    }

    private static boolean isDrawioXml(byte[] bytes) {
        String head = new String(bytes, 0, Math.min(bytes.length, 256), StandardCharsets.UTF_8);
        return head.contains("<mxfile") || head.contains("mxGraphModel");
    }
}
