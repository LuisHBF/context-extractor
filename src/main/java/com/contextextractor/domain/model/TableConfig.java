package com.contextextractor.domain.model;

public record TableConfig(
        String tableName,
        boolean exportDdl,
        boolean exportData,
        int rowLimit,
        String whereClause,
        String orderByClause
) {}
