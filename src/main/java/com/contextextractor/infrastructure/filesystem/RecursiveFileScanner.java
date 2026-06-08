package com.contextextractor.infrastructure.filesystem;

import com.contextextractor.domain.model.AppSettings;
import com.contextextractor.domain.model.ScannedFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class RecursiveFileScanner {

    private final AppSettings settings;
    private int lastSkippedCount;

    public RecursiveFileScanner(AppSettings settings) {
        this.settings = settings;
    }

    public List<ScannedFile> scan(Path rootDirectory) {
        lastSkippedCount = 0;
        List<ScannedFile> results = new ArrayList<>();

        try {
            Files.walkFileTree(rootDirectory, new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(rootDirectory) && isExcluded(dir.getFileName().toString())) {
                        lastSkippedCount++;
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (isExcluded(file.getFileName().toString())) {
                        lastSkippedCount++;
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        String content = Files.readString(file, StandardCharsets.UTF_8);
                        if (content.indexOf('\0') >= 0) {
                            lastSkippedCount++;
                            return FileVisitResult.CONTINUE;
                        }
                        String relativePath = rootDirectory.relativize(file).toString().replace('\\', '/');
                        results.add(new ScannedFile(relativePath, content));
                    } catch (IOException e) {
                        lastSkippedCount++;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    lastSkippedCount++;
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan directory: " + rootDirectory, e);
        }

        return results;
    }

    public int getLastSkippedCount() {
        return lastSkippedCount;
    }

    public List<ScannedFile> scanSingle(Path file) {
        if (isExcluded(file.getFileName().toString())) return List.of();
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.indexOf('\0') >= 0) return List.of();
            return List.of(new ScannedFile(file.getFileName().toString(), content));
        } catch (IOException e) {
            return List.of();
        }
    }

    private boolean isExcluded(String name) {
        String lower = name.toLowerCase();
        return settings.excludedPatterns().stream()
                .anyMatch(pattern -> {
                    String p = pattern.toLowerCase();
                    return lower.equals(p) || lower.endsWith(p);
                });
    }
}
