package com.contextextractor.domain.model;

import java.nio.file.Path;
import java.util.List;

public record AppSettings(int maxXmlSizeMb, List<String> excludedPatterns, String outputDirectory, String presetsDirectory) {

    public static AppSettings defaults() {
        String base = detectJarDirectory();
        String output = base != null ? base : System.getProperty("user.home") + "/Downloads";
        String presets = base != null ? base + "/presets" : System.getProperty("user.home") + "/.context-extractor/presets";
        return new AppSettings(6, List.of(
                "node_modules", ".git", "target", "build",
                ".class", ".jar", ".exe",
                ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg",
                ".woff", ".ttf", ".lock", ".DS_Store"
        ), output, presets);
    }

    private static String detectJarDirectory() {
        try {
            Path codeSource = Path.of(AppSettings.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (codeSource.toString().endsWith(".jar")) {
                return codeSource.getParent().toString();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
