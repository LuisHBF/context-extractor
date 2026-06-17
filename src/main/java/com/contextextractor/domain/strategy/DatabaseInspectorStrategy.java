package com.contextextractor.domain.strategy;

import com.contextextractor.domain.model.DatabaseConfig;
import com.contextextractor.domain.model.TableConfig;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Extension point for database introspection. Implementations provide
 * connectivity testing, schema and table discovery, DDL extraction, and
 * sample-data fetching for a specific database engine.
 *
 * <p>All methods receive a {@link DatabaseConfig} so that implementations
 * remain stateless and safe for concurrent use from parallel query threads.
 */
public interface DatabaseInspectorStrategy {

    /**
     * Opens a connection and verifies that it is live. Throws on any failure.
     *
     * @param config connection parameters
     * @throws SQLException if the connection cannot be established or the server rejects it
     */
    void testConnection(DatabaseConfig config) throws SQLException;

    /**
     * Returns the names of all user-defined schemas in the target database,
     * excluding internal PostgreSQL schemas ({@code pg_*} and {@code information_schema}).
     *
     * @param config connection parameters
     * @return schema names ordered alphabetically
     * @throws SQLException on connection or query failure
     */
    List<String> listSchemas(DatabaseConfig config) throws SQLException;

    /**
     * Returns the names of all base tables (not views) in the given schema.
     *
     * @param config connection parameters
     * @param schema the schema to inspect
     * @return table names ordered alphabetically
     * @throws SQLException on connection or query failure
     */
    List<String> listTables(DatabaseConfig config, String schema) throws SQLException;

    /**
     * Reconstructs a {@code CREATE TABLE} DDL statement for a single table, including
     * column types, nullability, defaults, primary key, and foreign key constraints.
     *
     * @param config    connection parameters
     * @param schema    schema containing the table
     * @param tableName target table name
     * @return a formatted DDL string ready to embed in the context file
     * @throws SQLException on connection or query failure
     */
    String fetchDdl(DatabaseConfig config, String schema, String tableName) throws SQLException;

    /**
     * Fetches a sample of rows from a table according to the export configuration
     * (optional WHERE/ORDER BY clauses and a row limit).
     * Each row is a {@link java.util.LinkedHashMap} of column name → string value,
     * preserving column declaration order.
     *
     * @param config      connection parameters
     * @param tableConfig export options: table name, WHERE clause, ORDER BY clause, and row limit
     * @return ordered list of rows; empty if no rows match the filter
     * @throws SQLException on connection or query failure
     */
    List<Map<String, String>> fetchData(DatabaseConfig config, TableConfig tableConfig) throws SQLException;
}
