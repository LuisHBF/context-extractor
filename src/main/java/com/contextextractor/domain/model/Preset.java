package com.contextextractor.domain.model;

import java.util.List;

public record Preset(
        String name,
        String agentFilePath,
        String directoryPath,
        DatabaseConfig databaseConfig,
        String selectedSchema,
        List<TableConfig> tableConfigs,
        String additionalContext,
        AppSettings settings,
        String outputFileName
) {}
