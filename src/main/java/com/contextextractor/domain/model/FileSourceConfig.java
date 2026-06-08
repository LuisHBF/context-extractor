package com.contextextractor.domain.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record FileSourceConfig(List<FileSource> sources) {

    public List<ScannedFile> allFiles() {
        Set<String> seen = new LinkedHashSet<>();
        List<ScannedFile> result = new ArrayList<>();
        for (FileSource source : sources) {
            for (ScannedFile file : source.includedFiles()) {
                Path abs = source.type() == FileSourceType.DIRECTORY
                        ? source.path().resolve(file.relativePath()).normalize()
                        : source.path();
                if (!source.excludedPaths().contains(abs) && seen.add(abs.toString())) {
                    result.add(file);
                }
            }
        }
        return List.copyOf(result);
    }
}
