package ru.gitverse.adoct.bugreport;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipFile;

public class BugReportWriterTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void writesReportWithStacktraceAndZipsPayload() throws Exception {
        Map<String, String> context = new LinkedHashMap<>();
        context.put("url", "https://confluence.example.com/pages/viewpage.action?pageId=42");
        BugReport report = new BugReport("import", "2026-06-11T10:00:00", "1.2.3", context,
                new IllegalStateException("boom"));

        Path reportDir = temp.getRoot().toPath().resolve("doc-bugreport-now");
        BugReportWriter.Result result = new BugReportWriter().write(report, reportDir,
                payload -> Files.writeString(payload.resolve("input.adoc"), "= Anon", StandardCharsets.UTF_8));

        Path text = reportDir.resolve("report.txt");
        Assert.assertTrue(Files.exists(text));
        String body = Files.readString(text, StandardCharsets.UTF_8);
        Assert.assertTrue(body.contains("operation : import"));
        Assert.assertTrue(body.contains("boom"));
        Assert.assertTrue(body.contains("IllegalStateException"));
        Assert.assertTrue(Files.exists(reportDir.resolve("payload").resolve("input.adoc")));

        Assert.assertTrue(Files.exists(result.zipFile()));
        try (ZipFile zip = new ZipFile(result.zipFile().toFile())) {
            Assert.assertNotNull(zip.getEntry("report.txt"));
            Assert.assertNotNull(zip.getEntry("payload/input.adoc"));
        }
    }

    @Test
    public void payloadFailureDoesNotFailReport() throws Exception {
        BugReport report = new BugReport("export", "2026-06-11T10:00:00", "1.0.0", Map.of(),
                new RuntimeException("nope"));
        Path reportDir = temp.getRoot().toPath().resolve("export-bugreport-now");

        BugReportWriter.Result result = new BugReportWriter().write(report, reportDir, payload -> {
            throw new IllegalStateException("anonymizer exploded");
        });

        Assert.assertTrue(Files.exists(reportDir.resolve("report.txt")));
        Assert.assertTrue(Files.exists(reportDir.resolve("payload-error.txt")));
        Assert.assertTrue(Files.exists(result.zipFile()));
    }
}
