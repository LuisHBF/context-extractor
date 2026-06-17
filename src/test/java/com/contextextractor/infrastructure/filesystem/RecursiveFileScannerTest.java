package com.contextextractor.infrastructure.filesystem;

import com.contextextractor.domain.model.AppSettings;
import com.contextextractor.domain.model.ScannedFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class RecursiveFileScannerTest {

    private Path tempDir;
    private RecursiveFileScanner scanner;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("scanner-test");
        AppSettings settings = new AppSettings(6, List.of(
                "node_modules", ".git", ".class", ".jar", ".png"
        ), "", "", false);
        scanner = new RecursiveFileScanner(settings);
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> p.toFile().delete());
    }

    @Test
    @DisplayName("Returns all readable text files from a flat directory")
    void returnsAllTextFilesFromFlatDirectory() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "aaa");
        Files.writeString(tempDir.resolve("b.txt"), "bbb");
        Files.writeString(tempDir.resolve("c.txt"), "ccc");

        List<ScannedFile> result = scanner.scan(tempDir);

        assertEquals(3, result.size());
        Set<String> names = result.stream().map(ScannedFile::relativePath).collect(Collectors.toSet());
        assertTrue(names.contains("a.txt"));
        assertTrue(names.contains("b.txt"));
        assertTrue(names.contains("c.txt"));
    }

    @Test
    @DisplayName("Recursively collects files from nested subdirectories")
    void recursivelyCollectsFilesFromNestedDirs() throws IOException {
        Path sub = Files.createDirectories(tempDir.resolve("src/main"));
        Files.writeString(sub.resolve("App.java"), "class App {}");
        Files.writeString(tempDir.resolve("README.md"), "readme");

        List<ScannedFile> result = scanner.scan(tempDir);

        Set<String> paths = result.stream().map(ScannedFile::relativePath).collect(Collectors.toSet());
        assertTrue(paths.contains("src/main/App.java"));
        assertTrue(paths.contains("README.md"));
    }

    @Test
    @DisplayName("Excludes files whose names match an exclusion pattern")
    void excludesFilesByName() throws IOException {
        Files.writeString(tempDir.resolve("App.java"), "class App {}");
        Files.writeString(tempDir.resolve("App.class"), "binary");

        List<ScannedFile> result = scanner.scan(tempDir);

        assertEquals(1, result.size());
        assertEquals("App.java", result.get(0).relativePath());
    }

    @Test
    @DisplayName("Excludes directories whose names match an exclusion pattern and skips their contents")
    void excludesDirectoriesAndSkipsContents() throws IOException {
        Path nodeModules = Files.createDirectories(tempDir.resolve("node_modules/lodash"));
        Files.writeString(nodeModules.resolve("index.js"), "module.exports = {};");
        Files.writeString(tempDir.resolve("app.js"), "console.log('hi');");

        List<ScannedFile> result = scanner.scan(tempDir);

        assertEquals(1, result.size());
        assertEquals("app.js", result.get(0).relativePath());
    }

    @Test
    @DisplayName("Exclusion pattern matching is case-insensitive")
    void exclusionIsCaseInsensitive() throws IOException {
        Files.writeString(tempDir.resolve("image.PNG"), "fake png");

        List<ScannedFile> result = scanner.scan(tempDir);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Returns empty list when directory contains only excluded files")
    void returnsEmptyListForFullyExcludedDirectory() throws IOException {
        Files.writeString(tempDir.resolve("app.class"), "bytecode");
        Files.writeString(tempDir.resolve("lib.jar"), "archive");

        List<ScannedFile> result = scanner.scan(tempDir);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Skips binary files containing null bytes without throwing an exception")
    void skipsBinaryFilesWithNullBytes() throws IOException {
        byte[] binaryContent = new byte[]{0x48, 0x65, 0x00, 0x6C, 0x6F};
        Files.write(tempDir.resolve("binary.dat"), binaryContent);
        Files.writeString(tempDir.resolve("text.txt"), "hello");

        List<ScannedFile> result = scanner.scan(tempDir);

        assertEquals(1, result.size());
        assertEquals("text.txt", result.get(0).relativePath());
    }

    @Test
    @DisplayName("Relative paths use forward slashes and are relative to the scanned root directory")
    void relativePathsAreRelativeToRoot() throws IOException {
        Path nested = Files.createDirectories(tempDir.resolve("com/example"));
        Files.writeString(nested.resolve("Main.java"), "class Main {}");

        List<ScannedFile> result = scanner.scan(tempDir);

        assertEquals(1, result.size());
        assertEquals("com/example/Main.java", result.get(0).relativePath());
        assertFalse(result.get(0).relativePath().contains("\\"));
    }

    @Test
    @DisplayName("Returns empty list when directory is empty")
    void returnsEmptyListForEmptyDirectory() {
        List<ScannedFile> result = scanner.scan(tempDir);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("""
            Given a directory containing both included and excluded files,
            when scan is called,
            then only the included files appear in the result
            """)
    void onlyIncludedFilesAppearInResult() throws IOException {
        Files.writeString(tempDir.resolve("App.java"), "class App {}");
        Files.writeString(tempDir.resolve("App.class"), "bytecode");
        Files.writeString(tempDir.resolve("readme.txt"), "docs");
        Files.writeString(tempDir.resolve("icon.png"), "fake png");
        Path gitDir = Files.createDirectories(tempDir.resolve(".git"));
        Files.writeString(gitDir.resolve("config"), "git config");

        List<ScannedFile> result = scanner.scan(tempDir);

        Set<String> paths = result.stream().map(ScannedFile::relativePath).collect(Collectors.toSet());
        assertEquals(Set.of("App.java", "readme.txt"), paths);
    }

    @Test
    @DisplayName("scanSingle returns the full absolute path with forward slashes")
    void scanSingleReturnsFullAbsolutePath() throws IOException {
        Path file = Files.writeString(tempDir.resolve("test.txt"), "hello world");

        List<ScannedFile> result = scanner.scanSingle(file);

        assertEquals(1, result.size());
        assertEquals("hello world", result.get(0).content());
        String path = result.get(0).relativePath();
        assertTrue(path.endsWith("/test.txt"));
        assertFalse(path.contains("\\"));
        assertTrue(path.contains(tempDir.getFileName().toString()));
        assertTrue(path.length() > "test.txt".length());
    }

    @Test
    @DisplayName("scanSingle returns empty list when file matches an exclusion pattern")
    void scanSingleReturnsEmptyForExcludedFile() throws IOException {
        Path file = Files.writeString(tempDir.resolve("icon.png"), "fake");

        List<ScannedFile> result = scanner.scanSingle(file);

        assertTrue(result.isEmpty());
    }
}
