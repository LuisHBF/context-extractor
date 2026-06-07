package com.contextextractor.application;

import com.contextextractor.domain.model.ContextPayload;
import com.contextextractor.domain.model.TableConfig;
import com.contextextractor.domain.strategy.ContextExportStrategy;
import com.contextextractor.domain.strategy.DatabaseInspectorStrategy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class GenerateContextUseCase {

    private final DatabaseInspectorStrategy dbInspector;
    private final ContextExportStrategy exporter;

    public record GenerationProgress(String message, double progress) {}
    public record GenerationResult(List<Path> outputFiles) {}

    public GenerateContextUseCase(DatabaseInspectorStrategy dbInspector, ContextExportStrategy exporter) {
        this.dbInspector = dbInspector;
        this.exporter = exporter;
    }

    public GenerationResult execute(ContextPayload payload, Consumer<GenerationProgress> progressCallback) throws Exception {
        progressCallback.accept(new GenerationProgress("Starting generation...", 0.0));

        progressCallback.accept(new GenerationProgress("Reading agent file...", 0.1));

        progressCallback.accept(new GenerationProgress("Collecting file contents...", 0.2));

        progressCallback.accept(new GenerationProgress("Fetching DDL for selected tables...", 0.4));
        Map<String, String> tableDdl = new LinkedHashMap<>();
        if (payload.databaseConfig() != null && !payload.databaseConfig().host().isBlank()
                && payload.tables() != null) {
            for (TableConfig tc : payload.tables()) {
                if (tc.exportDdl()) {
                    try {
                        String ddl = dbInspector.fetchDdl(
                                payload.databaseConfig(),
                                payload.databaseConfig().schema(),
                                tc.tableName());
                        tableDdl.put(tc.tableName(), ddl);
                    } catch (Exception ignored) {}
                }
            }
        }

        progressCallback.accept(new GenerationProgress("Fetching sample data for selected tables...", 0.6));
        Map<String, List<Map<String, String>>> tableData = new LinkedHashMap<>();
        if (payload.databaseConfig() != null && !payload.databaseConfig().host().isBlank()
                && payload.tables() != null) {
            for (TableConfig tc : payload.tables()) {
                if (tc.exportData()) {
                    try {
                        List<Map<String, String>> data = dbInspector.fetchData(payload.databaseConfig(), tc);
                        tableData.put(tc.tableName(), data);
                    } catch (Exception ignored) {}
                }
            }
        }

        progressCallback.accept(new GenerationProgress("Building XML...", 0.8));
        ContextPayload enriched = new ContextPayload(
                payload.agentContent(),
                payload.files(),
                payload.databaseConfig(),
                payload.tables() != null ? payload.tables() : List.of(),
                tableDdl,
                tableData,
                payload.additionalContext(),
                payload.settings(),
                payload.presetName(),
                payload.outputFileName());

        progressCallback.accept(new GenerationProgress("Writing output file(s)...", 0.9));
        String configuredDir = payload.settings().outputDirectory();
        Path outputDir = (configuredDir != null && !configuredDir.isBlank())
                ? Path.of(configuredDir)
                : Path.of(System.getProperty("user.home"), "Downloads");
        try {
            Files.createDirectories(outputDir);
        } catch (Exception ignored) {}
        if (!Files.isDirectory(outputDir)) {
            outputDir = Path.of(System.getProperty("user.home"), "Downloads");
            Files.createDirectories(outputDir);
        }
        List<Path> outputFiles = exporter.export(enriched, outputDir);

        int count = outputFiles.size();
        progressCallback.accept(new GenerationProgress("Done! " + count + " file(s) generated.", 1.0));

        return new GenerationResult(outputFiles);
    }
}
