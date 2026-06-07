package com.contextextractor.domain.strategy;

import com.contextextractor.domain.model.ContextPayload;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface ContextExportStrategy {
    List<Path> export(ContextPayload payload, Path outputDirectory) throws IOException;
}
