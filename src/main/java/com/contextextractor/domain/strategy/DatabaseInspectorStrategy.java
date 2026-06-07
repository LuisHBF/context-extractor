package com.contextextractor.domain.strategy;

import com.contextextractor.domain.model.DatabaseConfig;
import com.contextextractor.domain.model.TableConfig;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public interface DatabaseInspectorStrategy {
    void testConnection(DatabaseConfig config) throws SQLException;
    List<String> listSchemas(DatabaseConfig config) throws SQLException;
    List<String> listTables(DatabaseConfig config, String schema) throws SQLException;
    String fetchDdl(DatabaseConfig config, String schema, String tableName) throws SQLException;
    List<Map<String, String>> fetchData(DatabaseConfig config, TableConfig tableConfig) throws SQLException;
}
