package com.contextextractor.infrastructure.export;

import com.contextextractor.domain.model.AppSettings;
import com.contextextractor.domain.model.ContextPayload;
import com.contextextractor.domain.model.DatabaseConfig;
import com.contextextractor.domain.model.ScannedFile;
import com.contextextractor.domain.model.TableConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class XmlContextExporterTest {

    private XmlContextExporter exporter;
    private Path outputDir;

    @BeforeEach
    void setUp() throws IOException {
        exporter = new XmlContextExporter();
        outputDir = Files.createTempDirectory("xml-export-test");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(outputDir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> p.toFile().delete());
    }

    @Test
    @DisplayName("Generates valid XML with agent, files, database, and additionalContext sections")
    void generatesCompleteXmlWithAllSections() throws IOException {
        DatabaseConfig dbConfig = new DatabaseConfig("localhost", 5432, "testdb", "user", "pass", "public");
        TableConfig table = new TableConfig("users", true, true, 5, "", "");
        Map<String, String> tableDdl = Map.of("users", "CREATE TABLE users (id SERIAL PRIMARY KEY);");
        Map<String, List<Map<String, String>>> tableData = Map.of("users", List.of(Map.of("id", "1", "name", "Alice")));
        ContextPayload payload = new ContextPayload(
                "You are a helpful assistant.",
                Map.of("src/Main.java", "public class Main {}"),
                dbConfig, List.of(table), tableDdl, tableData,
                "Business rule: users must be active.",
                AppSettings.defaults(), "test-preset", "output", List.of());

        List<Path> files = exporter.export(payload, outputDir);
        String xml = Files.readString(files.get(0));

        assertTrue(xml.contains("<agent>"));
        assertTrue(xml.contains("<files>"));
        assertTrue(xml.contains("<database schema=\"public\">"));
        assertTrue(xml.contains("<additionalContext>"));
    }

    @Test
    @DisplayName("Wraps all dynamic content in CDATA blocks")
    void wrapsDynamicContentInCdata() throws IOException {
        ContextPayload payload = buildMinimalPayload("Agent content here", Map.of("file.txt", "File content here"), "Additional notes");

        List<Path> files = exporter.export(payload, outputDir);
        String xml = Files.readString(files.get(0));

        assertTrue(xml.contains("<![CDATA["));
        assertTrue(xml.contains("Agent content here"));
        assertTrue(xml.contains("File content here"));
        assertTrue(xml.contains("Additional notes"));
    }

    @Test
    @DisplayName("Escapes CDATA closing sequence (]]>) found inside file content")
    void escapesCdataClosingSequenceInFileContent() throws IOException {
        String contentWithCdata = "var x = ']]>';";
        ContextPayload payload = buildMinimalPayload("", Map.of("test.js", contentWithCdata), "");

        List<Path> files = exporter.export(payload, outputDir);
        String xml = Files.readString(files.get(0));

        assertFalse(xml.contains("var x = ']]>';"));
        assertTrue(xml.contains("]]]]><![CDATA[>"));
    }

    @Test
    @DisplayName("Includes generated-at attribute in ISO-8601 format on the root element")
    void includesGeneratedAtAttributeInIso8601() throws IOException {
        ContextPayload payload = buildMinimalPayload("", Map.of("a.txt", "content"), "");

        List<Path> files = exporter.export(payload, outputDir);
        String xml = Files.readString(files.get(0));

        assertTrue(xml.matches("(?s).*generated-at=\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*\".*"));
    }

    @Test
    @DisplayName("Sets preset attribute to 'unsaved' when presetName is null")
    void setsPresetToUnsavedWhenNull() throws IOException {
        ContextPayload payload = new ContextPayload(
                "", Map.of("a.txt", "x"), null, List.of(), Map.of(), Map.of(),
                "", AppSettings.defaults(), null, "output", List.of());

        List<Path> files = exporter.export(payload, outputDir);
        String xml = Files.readString(files.get(0));

        assertTrue(xml.contains("preset=\"unsaved\""));
    }

    @Test
    @DisplayName("Omits <agent> section when agentContent is null or blank")
    void omitsAgentSectionWhenBlank() throws IOException {
        ContextPayload payload = buildMinimalPayload("", Map.of("a.txt", "x"), "");

        List<Path> files = exporter.export(payload, outputDir);
        String xml = Files.readString(files.get(0));
        String body = xml.substring(xml.indexOf("<context"));

        assertFalse(body.contains("<agent>"));
    }

    @Test
    @DisplayName("Omits <database> section when databaseConfig is null")
    void omitsDatabaseSectionWhenConfigIsNull() throws IOException {
        ContextPayload payload = new ContextPayload(
                "agent", Map.of("a.txt", "x"), null, List.of(), Map.of(), Map.of(),
                "context", AppSettings.defaults(), "preset", "output", List.of());

        List<Path> files = exporter.export(payload, outputDir);
        String xml = Files.readString(files.get(0));
        String body = xml.substring(xml.indexOf("<context"));

        assertFalse(body.contains("<database schema="));
    }

    @Test
    @DisplayName("Omits <database> section when tableDdl and tableData are both empty")
    void omitsDatabaseSectionWhenNoTableContent() throws IOException {
        DatabaseConfig dbConfig = new DatabaseConfig("localhost", 5432, "db", "u", "p", "public");
        ContextPayload payload = new ContextPayload(
                "", Map.of("a.txt", "x"), dbConfig, List.of(), Map.of(), Map.of(),
                "", AppSettings.defaults(), null, "output", List.of());

        List<Path> files = exporter.export(payload, outputDir);
        String xml = Files.readString(files.get(0));
        String body = xml.substring(xml.indexOf("<context"));

        assertFalse(body.contains("<database schema="));
    }

    @Test
    @DisplayName("Omits <additionalContext> section when additionalContext is null or blank")
    void omitsAdditionalContextWhenBlank() throws IOException {
        ContextPayload payload = buildMinimalPayload("agent", Map.of("a.txt", "x"), "");

        List<Path> files = exporter.export(payload, outputDir);
        String xml = Files.readString(files.get(0));
        String body = xml.substring(xml.indexOf("<context"));

        assertFalse(body.contains("<additionalContext>"));
    }

    @Test
    @DisplayName("""
            Given a payload whose XML representation exceeds the configured max size,
            when export is called,
            then multiple part files are created and each is a valid XML document
            """)
    void splitsIntoMultiplePartsWhenExceedingMaxSize() throws IOException {
        Map<String, String> largeFiles = new LinkedHashMap<>();
        String largeContent = "x".repeat(1024 * 1024);
        for (int i = 0; i < 5; i++) {
            largeFiles.put("file" + i + ".txt", largeContent);
        }

        AppSettings smallLimit = new AppSettings(1, List.of(), outputDir.toString(), "", false);
        ContextPayload payload = new ContextPayload(
                "", largeFiles, null, List.of(), Map.of(), Map.of(),
                "", smallLimit, null, "context", List.of());

        List<Path> files = exporter.export(payload, outputDir);

        assertTrue(files.size() > 1);
        for (Path file : files) {
            String content = Files.readString(file);
            assertTrue(content.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
            assertTrue(content.contains("</context>"));
        }
    }

    @Test
    @DisplayName("Single output file is named after the configured outputFileName")
    void outputFileUsesConfiguredName() throws IOException {
        ContextPayload payload = buildMinimalPayload("agent", Map.of("a.txt", "x"), "");

        List<Path> files = exporter.export(payload, outputDir);

        assertEquals("my-output.xml", files.get(0).getFileName().toString());
    }

    @Test
    @DisplayName("Part files are named context-part1.xml, context-part2.xml when split occurs")
    void partFilesFollowNamingConvention() throws IOException {
        Map<String, String> largeFiles = new LinkedHashMap<>();
        String largeContent = "y".repeat(1024 * 1024);
        for (int i = 0; i < 4; i++) {
            largeFiles.put("file" + i + ".txt", largeContent);
        }

        AppSettings smallLimit = new AppSettings(1, List.of(), outputDir.toString(), "", false);
        ContextPayload payload = new ContextPayload(
                "", largeFiles, null, List.of(), Map.of(), Map.of(),
                "", smallLimit, null, "context", List.of());

        List<Path> files = exporter.export(payload, outputDir);

        assertTrue(files.size() >= 2);
        assertTrue(files.get(0).getFileName().toString().contains("part1"));
        assertTrue(files.get(1).getFileName().toString().contains("part2"));
    }

    @Test
    @DisplayName("""
            Given a codeChanges entry with MODIFIED status,
            when export is called,
            then the <codeChanges> section contains a <file> element with status='MODIFIED'
            """)
    void includesCodeChangesSectionWithModifiedStatus() throws IOException {
        String diffContent = "diff --git a/src/Main.java b/src/Main.java\n"
                + "--- a/src/Main.java\n"
                + "+++ b/src/Main.java\n"
                + "@@ -1,3 +1,4 @@\n"
                + " public class Main {\n"
                + "+    int x;\n"
                + " }\n";
        ScannedFile codeChange = new ScannedFile("ALL_CHANGES|main|" + outputDir, diffContent);
        ContextPayload payload = new ContextPayload(
                "", Map.of("a.txt", "x"), null, List.of(), Map.of(), Map.of(),
                "context", AppSettings.defaults(), null, "output", List.of(codeChange));

        List<Path> files = exporter.export(payload, outputDir);
        String xml = Files.readString(files.get(0));

        assertTrue(xml.contains("<codeChanges"));
        assertTrue(xml.contains("status=\"MODIFIED\""));
    }

    @Test
    @DisplayName("Generates correct line annotations: space for unchanged, + for added, - for removed")
    void generatesCorrectLineAnnotations() throws IOException {
        Path srcFile = outputDir.resolve("Hello.java");
        Files.writeString(srcFile, "line1\nnewline\nline2\n");

        String diffContent = "diff --git a/Hello.java b/Hello.java\n"
                + "--- a/Hello.java\n"
                + "+++ b/Hello.java\n"
                + "@@ -1,2 +1,3 @@\n"
                + " line1\n"
                + "+newline\n"
                + " line2\n";
        ScannedFile codeChange = new ScannedFile("ALL_CHANGES|main|" + outputDir, diffContent);
        ContextPayload payload = new ContextPayload(
                "", Map.of("a.txt", "x"), null, List.of(), Map.of(), Map.of(),
                "", AppSettings.defaults(), null, "output", List.of(codeChange));

        List<Path> files = exporter.export(payload, outputDir);
        String xml = Files.readString(files.get(0));

        assertTrue(xml.contains("\u2502 + \u2502 newline"));
        assertTrue(xml.contains("\u2502   \u2502 line1"));
    }

    @Test
    @DisplayName("Deleted file entries show all lines annotated as removed")
    void deletedFileShowsAllLinesAsRemoved() throws IOException {
        String diffContent = "diff --git a/Old.java b/Old.java\n"
                + "--- a/Old.java\n"
                + "+++ /dev/null\n"
                + "@@ -1,2 +0,0 @@\n"
                + "-line1\n"
                + "-line2\n";
        ScannedFile codeChange = new ScannedFile("ALL_CHANGES|main|" + outputDir, diffContent);
        ContextPayload payload = new ContextPayload(
                "", Map.of("a.txt", "x"), null, List.of(), Map.of(), Map.of(),
                "", AppSettings.defaults(), null, "output", List.of(codeChange));

        List<Path> files = exporter.export(payload, outputDir);
        String xml = Files.readString(files.get(0));

        assertTrue(xml.contains("status=\"DELETED\""));
        assertTrue(xml.contains("\u2502 - \u2502 line1"));
        assertTrue(xml.contains("\u2502 - \u2502 line2"));
    }

    @Test
    @DisplayName("Added file entries show all lines annotated as added")
    void addedFileShowsAllLinesAsAdded() throws IOException {
        Path srcFile = outputDir.resolve("New.java");
        Files.writeString(srcFile, "hello\nworld\n");

        String diffContent = "diff --git a/New.java b/New.java\n"
                + "--- /dev/null\n"
                + "+++ b/New.java\n"
                + "@@ -0,0 +1,2 @@\n"
                + "+hello\n"
                + "+world\n";
        ScannedFile codeChange = new ScannedFile("ALL_CHANGES|main|" + outputDir, diffContent);
        ContextPayload payload = new ContextPayload(
                "", Map.of("a.txt", "x"), null, List.of(), Map.of(), Map.of(),
                "", AppSettings.defaults(), null, "output", List.of(codeChange));

        List<Path> files = exporter.export(payload, outputDir);
        String xml = Files.readString(files.get(0));

        assertTrue(xml.contains("status=\"ADDED\""));
        assertTrue(xml.contains("\u2502 + \u2502 hello"));
        assertTrue(xml.contains("\u2502 + \u2502 world"));
    }

    @Test
    @DisplayName("Includes generator attribute with application version from AppSettings.VERSION")
    void includesGeneratorAttributeWithVersion() throws IOException {
        ContextPayload payload = buildMinimalPayload("", Map.of("a.txt", "x"), "");

        List<Path> files = exporter.export(payload, outputDir);
        String xml = Files.readString(files.get(0));

        assertTrue(xml.contains("generator=\"context-extractor/" + AppSettings.VERSION + "\""));
    }

    @Test
    @DisplayName("""
            Given a ContextPayload with 3 source files and no database config,
            when export is called,
            then the generated XML contains exactly 3 <file> elements
            and no <database> section
            """)
    void generatesXmlWithFilesOnlyWhenNoDatabaseConfig() throws IOException {
        Map<String, String> threeFiles = Map.of(
                "A.java", "class A {}",
                "B.java", "class B {}",
                "C.java", "class C {}");
        ContextPayload payload = new ContextPayload(
                "", threeFiles, null, List.of(), Map.of(), Map.of(),
                "", AppSettings.defaults(), null, "output", List.of());

        List<Path> files = exporter.export(payload, outputDir);
        String xml = Files.readString(files.get(0));

        String body = xml.substring(xml.indexOf("<context"));
        long fileElementCount = body.lines().filter(l -> l.trim().startsWith("<file path=")).count();
        assertEquals(3, fileElementCount);
        assertFalse(body.contains("<database schema="));
    }

    private ContextPayload buildMinimalPayload(String agent, Map<String, String> files, String additional) {
        return new ContextPayload(
                agent, files, null, List.of(), Map.of(), Map.of(),
                additional, AppSettings.defaults(), "test", "my-output", List.of());
    }
}
