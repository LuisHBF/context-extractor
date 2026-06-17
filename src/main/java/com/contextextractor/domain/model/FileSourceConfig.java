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
            if (source.type() == FileSourceType.GIT_DIFF) continue;
            for (ScannedFile file : source.includedFiles()) {
                Path abs = source.type() == FileSourceType.DIRECTORY
                        ? source.path().resolve(file.relativePath()).normalize()
                        : source.path();
                String absPath = abs.toAbsolutePath().normalize().toString().replace('\\', '/');
                if (!source.excludedPaths().contains(abs) && seen.add(absPath)) {
                    result.add(new ScannedFile(absPath, file.content()));
                }
            }
        }
        return List.copyOf(result);
    }

    public List<FileSource> gitDiffSources() {
        return sources.stream()
                .filter(s -> s.type() == FileSourceType.GIT_DIFF)
                .toList();
    }
}
