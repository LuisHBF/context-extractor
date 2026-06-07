package com.contextextractor.domain.model;

import java.util.List;
import java.util.Map;

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
        String outputFileName
) {}
