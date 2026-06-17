package com.contextextractor.domain.strategy;

import com.contextextractor.domain.model.ContextPayload;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Extension point for context file export. Implementations serialise a
 * {@link ContextPayload} into one or more output files in a chosen format
 * (XML, Markdown, JSON, etc.).
 *
 * <p>A single logical export may produce multiple files when the payload
 * exceeds the configured size limit. In that case each file is a self-contained
 * part labelled {@code name-part1.ext}, {@code name-part2.ext}, etc.
 */
public interface ContextExportStrategy {

    /**
     * Serialises the given payload and writes one or more files to {@code outputDirectory}.
     * If the entire payload fits within the configured size limit, exactly one file is
     * returned. If it is too large, the file set is split across multiple parts.
     *
     * @param payload         the fully enriched payload aggregating all user selections
     * @param outputDirectory the directory to write output files into; must exist and be writable
     * @return ordered list of written file paths; never empty
     * @throws IOException if a file cannot be written
     */
    List<Path> export(ContextPayload payload, Path outputDirectory) throws IOException;
}
