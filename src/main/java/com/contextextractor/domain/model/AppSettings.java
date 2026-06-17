package com.contextextractor.domain.model;

import java.util.List;

public record AppSettings(
        int maxXmlSizeMb,
        List<String> excludedPatterns,
        String outputDirectory,
        String presetsDirectory,
        boolean darkMode) {

    public static final String VERSION = "1.2.0";

    public static AppSettings defaults() {
        String home = System.getProperty("user.home");
        String output = home + "/.context-extractor/outputs";
        String presets = home + "/.context-extractor/presets";
        return new AppSettings(6, List.of(
                "node_modules", ".git", "target", "build",
                ".class", ".jar", ".exe",
                ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg",
                ".woff", ".ttf", ".lock", ".DS_Store"
        ), output, presets, false);
    }

}
