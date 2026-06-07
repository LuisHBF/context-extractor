package com.contextextractor.domain.model;

import java.nio.file.Path;
import java.util.List;

public record DirectoryConfig(Path rootPath, List<ScannedFile> files) {}
