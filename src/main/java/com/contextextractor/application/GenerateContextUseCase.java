package com.contextextractor.application;

import com.contextextractor.domain.model.ContextPayload;
import com.contextextractor.domain.model.TableConfig;
import com.contextextractor.domain.strategy.ContextExportStrategy;
import com.contextextractor.domain.strategy.DatabaseInspectorStrategy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class GenerateContextUseCase {

    private final DatabaseInspectorStrategy dbInspector;
    private final ContextExportStrategy exporter;

    /** Carries a human-readable status message and a 0.0–1.0 progress fraction. */
    public record GenerationProgress(String message, double progress) {}

    /** Holds the paths of all files written by a successful generation run. */
    public record GenerationResult(List<Path> outputFiles) {}

    public GenerateContextUseCase(DatabaseInspectorStrategy dbInspector, ContextExportStrategy exporter) {
        this.dbInspector = Objects.requireNonNull(dbInspector, "dbInspector must not be null");
        this.exporter = Objects.requireNonNull(exporter, "exporter must not be null");
    }

    /**
     * Orchestrates the full context generation pipeline.
     * Fetches DDL and sample data in parallel, assembles the enriched payload,
     * and delegates export to the configured {@link ContextExportStrategy}.
     *
     * @param payload          the aggregated user selections from all steps
     * @param progressCallback receives progress updates for UI binding; called on the background thread
     * @return the list of generated output file paths
     * @throws Exception if file I/O or the output directory cannot be resolved
     */
    public GenerationResult execute(ContextPayload payload, Consumer<GenerationProgress> progressCallback) throws Exception {
        report(progressCallback, "Starting generation...", 0.0);
        report(progressCallback, "Collecting file contents...", 0.2);

        Map<String, String> tableDdl = fetchDdlInParallel(payload, progressCallback);
        Map<String, List<Map<String, String>>> tableData = fetchDataInParallel(payload, progressCallback);

        report(progressCallback, "Building XML...", 0.7);
        ContextPayload enriched = enrich(payload, tableDdl, tableData);

        report(progressCallback, "Writing output file(s)...", 0.9);
        List<Path> outputFiles = writeOutput(enriched);

        report(progressCallback, "Done! " + outputFiles.size() + " file(s) generated.", 1.0);
        return new GenerationResult(outputFiles);
    }

    private Map<String, String> fetchDdlInParallel(
            ContextPayload payload, Consumer<GenerationProgress> progressCallback) throws InterruptedException {
        report(progressCallback, "Fetching DDL for selected tables...", 0.4);
        Map<String, String> tableDdl = new LinkedHashMap<>();
        if (!hasDatabaseConfig(payload)) return tableDdl;

        List<TableConfig> ddlTables = payload.tables().stream().filter(TableConfig::exportDdl).toList();
        if (ddlTables.isEmpty()) return tableDdl;

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(ddlTables.size(), 8), daemonThreadFactory());
        List<Future<Map.Entry<String, String>>> futures = ddlTables.stream()
                .map(tableConfig -> executor.submit(() -> {
                    try {
                        String ddl = dbInspector.fetchDdl(
                                payload.databaseConfig(), payload.databaseConfig().schema(), tableConfig.tableName());
                        return Map.entry(tableConfig.tableName(), ddl);
                    } catch (Exception e) {
                        /* catch broadly — one table failure must not abort the entire batch */
                        return Map.entry(tableConfig.tableName(), "-- DDL unavailable: " + e.getMessage());
                    }
                }))
                .toList();

        executor.shutdown();
        if (!executor.awaitTermination(120, TimeUnit.SECONDS)) executor.shutdownNow();

        for (Future<Map.Entry<String, String>> future : futures) {
            try {
                if (future.isDone()) {
                    Map.Entry<String, String> entry = future.get();
                    tableDdl.put(entry.getKey(), entry.getValue());
                }
            } catch (Exception ignored) { /* future completed with exception; DDL entry is omitted */ }
        }
        return tableDdl;
    }

    private Map<String, List<Map<String, String>>> fetchDataInParallel(
            ContextPayload payload, Consumer<GenerationProgress> progressCallback) throws InterruptedException {
        report(progressCallback, "Fetching sample data for selected tables...", 0.5);
        Map<String, List<Map<String, String>>> tableData = new LinkedHashMap<>();
        if (!hasDatabaseConfig(payload)) return tableData;

        List<TableConfig> dataTables = payload.tables().stream().filter(TableConfig::exportData).toList();
        if (dataTables.isEmpty()) return tableData;

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(dataTables.size(), 8), daemonThreadFactory());
        List<Future<Map.Entry<String, List<Map<String, String>>>>> futures = dataTables.stream()
                .map(tableConfig -> executor.submit(() -> {
                    try {
                        List<Map<String, String>> rows = dbInspector.fetchData(payload.databaseConfig(), tableConfig);
                        return Map.entry(tableConfig.tableName(), rows);
                    } catch (Exception e) {
                        /* catch broadly — one table failure must not abort the entire batch */
                        return Map.entry(tableConfig.tableName(), List.<Map<String, String>>of());
                    }
                }))
                .toList();

        executor.shutdown();
        if (!executor.awaitTermination(120, TimeUnit.SECONDS)) executor.shutdownNow();

        for (Future<Map.Entry<String, List<Map<String, String>>>> future : futures) {
            try {
                if (future.isDone()) {
                    Map.Entry<String, List<Map<String, String>>> entry = future.get();
                    if (!entry.getValue().isEmpty()) tableData.put(entry.getKey(), entry.getValue());
                }
            } catch (Exception ignored) { /* future completed with exception; data entry is omitted */ }
        }
        return tableData;
    }

    private ContextPayload enrich(ContextPayload payload,
                                   Map<String, String> tableDdl,
                                   Map<String, List<Map<String, String>>> tableData) {
        return new ContextPayload(
                payload.agentContent(),
                payload.files(),
                payload.databaseConfig(),
                Objects.nonNull(payload.tables()) ? payload.tables() : List.of(),
                tableDdl,
                tableData,
                payload.additionalContext(),
                payload.settings(),
                payload.presetName(),
                payload.outputFileName(),
                payload.codeChanges());
    }

    private List<Path> writeOutput(ContextPayload enriched) throws Exception {
        String configuredDir = enriched.settings().outputDirectory();
        Path outputDir = (Objects.nonNull(configuredDir) && !configuredDir.isBlank())
                ? Path.of(configuredDir)
                : Path.of(System.getProperty("user.home"), "Downloads");
        try {
            Files.createDirectories(outputDir);
        } catch (Exception ignored) { /* best-effort; validated below */ }
        if (!Files.isDirectory(outputDir)) {
            outputDir = Path.of(System.getProperty("user.home"), "Downloads");
            Files.createDirectories(outputDir);
        }
        return exporter.export(enriched, outputDir);
    }

    private boolean hasDatabaseConfig(ContextPayload payload) {
        return Objects.nonNull(payload.databaseConfig())
                && !payload.databaseConfig().host().isBlank()
                && Objects.nonNull(payload.tables())
                && !payload.tables().isEmpty();
    }

    private void report(Consumer<GenerationProgress> callback, String message, double progress) {
        callback.accept(new GenerationProgress(message, progress));
    }

    private ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        };
    }
}
