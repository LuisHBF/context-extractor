package com.contextextractor.domain.model;

import java.util.List;
import java.util.Map;

/**
 * Aggregates all data collected across the five wizard steps, immediately
 * before export. Instances are immutable.
 *
 * <p>The two-phase construction pattern is intentional: the presentation layer
 * builds a {@code ContextPayload} with empty {@code tableDdl} and {@code tableData}
 * maps, passes it to {@code GenerateContextUseCase}, which queries the database
 * in parallel and returns an enriched copy. The exporter receives the enriched copy.
 *
 * @param agentContent      raw text of the selected agent/system-prompt file; may be blank
 * @param files             relative file path → file content for all scanned source files
 * @param databaseConfig    PostgreSQL connection parameters; {@code null} when no DB is configured
 * @param tables            tables selected for export; empty when no database is configured
 * @param tableDdl          table name → reconstructed CREATE TABLE DDL; populated by the use case
 * @param tableData         table name → list of rows (column name → value); populated by the use case
 * @param additionalContext free-form developer notes appended at the end of the context file
 * @param settings          application settings in effect at generation time
 * @param presetName        name of the preset used to populate this payload; {@code null} for unsaved sessions
 * @param outputFileName    base name for output file(s) without extension; defaults to {@code "context"}
 * @param codeChanges       git diff entries, each encoded as a {@code ScannedFile} whose
 *                          {@code relativePath} is {@code "MODE|/repo/path"} and whose
 *                          {@code content} is the raw diff text; empty when not configured
 */
public record ContextPayload(
        String agentContent,
        Map<String, String> files,
        DatabaseConfig databaseConfig,
        List<TableConfig> tables,
        Map<String, String> tableDdl,
        Map<String, List<Map<String, String>>> tableData,
        String additionalContext,
        AppSettings settings,
        String presetName,
        String outputFileName,
        List<ScannedFile> codeChanges
) {}
