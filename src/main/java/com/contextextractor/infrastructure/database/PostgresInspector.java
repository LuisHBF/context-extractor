package com.contextextractor.infrastructure.database;

import com.contextextractor.domain.model.DatabaseConfig;
import com.contextextractor.domain.model.TableConfig;
import com.contextextractor.domain.strategy.DatabaseInspectorStrategy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PostgresInspector implements DatabaseInspectorStrategy {

    private static final String LIST_SCHEMAS_SQL = """
            SELECT schema_name
            FROM information_schema.schemata
            WHERE schema_name NOT LIKE 'pg_%'
              AND schema_name != 'information_schema'
            ORDER BY schema_name
            """;

    private static final String LIST_TABLES_SQL = """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = ?
              AND table_type = 'BASE TABLE'
            ORDER BY table_name
            """;

    private static final String COLUMNS_SQL = """
            SELECT column_name, data_type, udt_name, character_maximum_length,
                   numeric_precision, numeric_scale, is_nullable, column_default
            FROM information_schema.columns
            WHERE table_schema = ? AND table_name = ?
            ORDER BY ordinal_position
            """;

    private static final String PRIMARY_KEY_SQL = """
            SELECT kcu.column_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
                ON tc.constraint_name = kcu.constraint_name
                AND tc.table_schema = kcu.table_schema
            WHERE tc.constraint_type = 'PRIMARY KEY'
              AND tc.table_schema = ?
              AND tc.table_name = ?
            ORDER BY kcu.ordinal_position
            """;

    private static final String FOREIGN_KEY_SQL = """
            SELECT tc.constraint_name, kcu.column_name,
                   ccu.table_schema AS foreign_schema,
                   ccu.table_name   AS foreign_table,
                   ccu.column_name  AS foreign_column
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
                ON tc.constraint_name = kcu.constraint_name
                AND tc.table_schema   = kcu.table_schema
            JOIN information_schema.constraint_column_usage ccu
                ON ccu.constraint_name = tc.constraint_name
                AND ccu.table_schema   = tc.table_schema
            WHERE tc.constraint_type = 'FOREIGN KEY'
              AND tc.table_schema = ?
              AND tc.table_name   = ?
            ORDER BY tc.constraint_name, kcu.ordinal_position
            """;

    @Override
    public void testConnection(DatabaseConfig config) throws SQLException {
        try (Connection conn = DriverManager.getConnection(config.jdbcUrl(), config.username(), config.password())) {
            if (!conn.isValid(5)) throw new SQLException("Connection established but reported as invalid by the driver");
        }
    }

    @Override
    public List<String> listSchemas(DatabaseConfig config) throws SQLException {
        List<String> schemas = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(config.jdbcUrl(), config.username(), config.password());
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(30);
            try (ResultSet rs = stmt.executeQuery(LIST_SCHEMAS_SQL)) {
                while (rs.next()) schemas.add(rs.getString(1));
            }
        }
        return schemas;
    }

    @Override
    public List<String> listTables(DatabaseConfig config, String schema) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(config.jdbcUrl(), config.username(), config.password());
             PreparedStatement ps = conn.prepareStatement(LIST_TABLES_SQL)) {
            ps.setQueryTimeout(30);
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) tables.add(rs.getString(1));
            }
        }
        return tables;
    }

    @Override
    public String fetchDdl(DatabaseConfig config, String schema, String tableName) throws SQLException {
        try (Connection conn = DriverManager.getConnection(config.jdbcUrl(), config.username(), config.password())) {
            return buildDdl(conn, schema, tableName);
        }
    }

    @Override
    public List<Map<String, String>> fetchData(DatabaseConfig config, TableConfig tableConfig) throws SQLException {
        StringBuilder queryBuilder = new StringBuilder("SELECT * FROM \"")
                .append(config.schema()).append("\".\"").append(tableConfig.tableName()).append("\"");
        if (!tableConfig.whereClause().isBlank()) {
            queryBuilder.append(" WHERE ").append(tableConfig.whereClause());
        }
        if (!tableConfig.orderByClause().isBlank()) {
            queryBuilder.append(" ORDER BY ").append(tableConfig.orderByClause());
        }
        queryBuilder.append(" LIMIT ").append(tableConfig.rowLimit());

        List<Map<String, String>> rows = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(config.jdbcUrl(), config.username(), config.password());
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(30);
            try (ResultSet rs = stmt.executeQuery(queryBuilder.toString())) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, String> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(meta.getColumnName(i), rs.getString(i));
                    }
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private String buildDdl(Connection conn, String schema, String tableName) throws SQLException {
        List<String> pkColumns = fetchPrimaryKeyColumns(conn, schema, tableName);
        List<String> columnDefs = buildColumnDefinitions(conn, schema, tableName, pkColumns);
        appendForeignKeyConstraints(conn, schema, tableName, columnDefs);
        return "CREATE TABLE " + schema + "." + tableName + " (\n"
                + String.join(",\n", columnDefs) + "\n);";
    }

    private List<String> fetchPrimaryKeyColumns(Connection conn, String schema, String tableName) throws SQLException {
        List<String> pkColumns = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(PRIMARY_KEY_SQL)) {
            ps.setQueryTimeout(30);
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) pkColumns.add(rs.getString(1));
            }
        }
        return pkColumns;
    }

    private List<String> buildColumnDefinitions(Connection conn, String schema, String tableName,
                                                  List<String> pkColumns) throws SQLException {
        List<String> columnDefs = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(COLUMNS_SQL)) {
            ps.setQueryTimeout(30);
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StringBuilder columnDef = new StringBuilder("    ");
                    columnDef.append(rs.getString("column_name")).append(" ");
                    columnDef.append(formatColumnType(
                            rs.getString("data_type"),
                            rs.getString("udt_name"),
                            (Integer) rs.getObject("character_maximum_length"),
                            (Integer) rs.getObject("numeric_precision"),
                            (Integer) rs.getObject("numeric_scale")));
                    if ("NO".equals(rs.getString("is_nullable"))) columnDef.append(" NOT NULL");
                    String columnDefault = rs.getString("column_default");
                    if (Objects.nonNull(columnDefault)) columnDef.append(" DEFAULT ").append(columnDefault);
                    columnDefs.add(columnDef.toString());
                }
            }
        }
        if (!pkColumns.isEmpty()) {
            columnDefs.add("    PRIMARY KEY (" + String.join(", ", pkColumns) + ")");
        }
        return columnDefs;
    }

    private String formatColumnType(String dataType, String udtName,
                                     Integer charMaxLen, Integer numPrecision, Integer numScale) {
        if ("USER-DEFINED".equals(dataType) || "ARRAY".equals(dataType)) return Objects.nonNull(udtName) ? udtName.toUpperCase() : dataType;
        if (Objects.nonNull(charMaxLen)) return dataType.toUpperCase() + "(" + charMaxLen + ")";
        if (Objects.nonNull(numPrecision) && Objects.nonNull(numScale) && numScale > 0) {
            return dataType.toUpperCase() + "(" + numPrecision + "," + numScale + ")";
        }
        return dataType.toUpperCase();
    }

    private void appendForeignKeyConstraints(Connection conn, String schema, String tableName,
                                              List<String> columnDefs) throws SQLException {
        Map<String, List<String>> fkLocalCols = new LinkedHashMap<>();
        Map<String, String> fkForeignTable = new LinkedHashMap<>();
        Map<String, List<String>> fkForeignCols = new LinkedHashMap<>();

        try (PreparedStatement ps = conn.prepareStatement(FOREIGN_KEY_SQL)) {
            ps.setQueryTimeout(30);
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String constraintName = rs.getString("constraint_name");
                    fkLocalCols.computeIfAbsent(constraintName, k -> new ArrayList<>())
                               .add(rs.getString("column_name"));
                    fkForeignTable.putIfAbsent(constraintName,
                            rs.getString("foreign_schema") + "." + rs.getString("foreign_table"));
                    fkForeignCols.computeIfAbsent(constraintName, k -> new ArrayList<>())
                                 .add(rs.getString("foreign_column"));
                }
            }
        }

        for (String constraintName : fkLocalCols.keySet()) {
            columnDefs.add("    CONSTRAINT " + constraintName
                    + " FOREIGN KEY (" + String.join(", ", fkLocalCols.get(constraintName)) + ")"
                    + " REFERENCES " + fkForeignTable.get(constraintName)
                    + " (" + String.join(", ", fkForeignCols.get(constraintName)) + ")");
        }
    }
}
