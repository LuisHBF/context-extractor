package com.contextextractor.domain.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public record FileSource(
        FileSourceType type,
        Path path,
        List<ScannedFile> includedFiles,
        Set<Path> excludedPaths
) {}
