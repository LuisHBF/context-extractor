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

public class PostgresInspector implements DatabaseInspectorStrategy {

    @Override
    public void testConnection(DatabaseConfig config) throws SQLException {
        try (Connection conn = DriverManager.getConnection(config.jdbcUrl(), config.username(), config.password())) {
            conn.isValid(5);
        }
    }

    @Override
    public List<String> listSchemas(DatabaseConfig config) throws SQLException {
        String sql = """
                SELECT schema_name
                FROM information_schema.schemata
                WHERE schema_name NOT LIKE 'pg_%'
                  AND schema_name != 'information_schema'
                ORDER BY schema_name
                """;
        List<String> schemas = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(config.jdbcUrl(), config.username(), config.password());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                schemas.add(rs.getString(1));
            }
        }
        return schemas;
    }

    @Override
    public List<String> listTables(DatabaseConfig config, String schema) throws SQLException {
        String sql = """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = ?
                  AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """;
        List<String> tables = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(config.jdbcUrl(), config.username(), config.password());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
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
        StringBuilder sql = new StringBuilder("SELECT * FROM \"")
                .append(config.schema()).append("\".\"").append(tableConfig.tableName()).append("\"");

        if (!tableConfig.whereClause().isBlank()) {
            sql.append(" WHERE ").append(tableConfig.whereClause());
        }
        if (!tableConfig.orderByClause().isBlank()) {
            sql.append(" ORDER BY ").append(tableConfig.orderByClause());
        }
        sql.append(" LIMIT ").append(tableConfig.rowLimit());

        List<Map<String, String>> rows = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(config.jdbcUrl(), config.username(), config.password());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql.toString())) {
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
        return rows;
    }

    private String buildDdl(Connection conn, String schema, String tableName) throws SQLException {
        String colSql = """
                SELECT column_name, data_type, udt_name, character_maximum_length,
                       numeric_precision, numeric_scale, is_nullable, column_default
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ?
                ORDER BY ordinal_position
                """;

        String pkSql = """
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

        List<String> pkCols = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(pkSql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) pkCols.add(rs.getString(1));
            }
        }

        List<String> colDefs = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(colSql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StringBuilder col = new StringBuilder("    ");
                    col.append(rs.getString("column_name")).append(" ");

                    String dataType = rs.getString("data_type");
                    String udtName = rs.getString("udt_name");
                    Integer charMaxLen = (Integer) rs.getObject("character_maximum_length");
                    Integer numPrecision = (Integer) rs.getObject("numeric_precision");
                    Integer numScale = (Integer) rs.getObject("numeric_scale");

                    if ("USER-DEFINED".equals(dataType) || "ARRAY".equals(dataType)) {
                        col.append(udtName.toUpperCase());
                    } else if (charMaxLen != null) {
                        col.append(dataType.toUpperCase()).append("(").append(charMaxLen).append(")");
                    } else if (numPrecision != null && numScale != null && numScale > 0) {
                        col.append(dataType.toUpperCase()).append("(").append(numPrecision).append(",").append(numScale).append(")");
                    } else {
                        col.append(dataType.toUpperCase());
                    }

                    if ("NO".equals(rs.getString("is_nullable"))) {
                        col.append(" NOT NULL");
                    }
                    String def = rs.getString("column_default");
                    if (def != null) {
                        col.append(" DEFAULT ").append(def);
                    }
                    colDefs.add(col.toString());
                }
            }
        }

        if (!pkCols.isEmpty()) {
            colDefs.add("    PRIMARY KEY (" + String.join(", ", pkCols) + ")");
        }

        String fkSql = """
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

        Map<String, List<String>> fkLocalCols = new LinkedHashMap<>();
        Map<String, String> fkForeignTable = new LinkedHashMap<>();
        Map<String, List<String>> fkForeignCols = new LinkedHashMap<>();

        try (PreparedStatement ps = conn.prepareStatement(fkSql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("constraint_name");
                    fkLocalCols.computeIfAbsent(name, k -> new ArrayList<>()).add(rs.getString("column_name"));
                    fkForeignTable.putIfAbsent(name,
                            rs.getString("foreign_schema") + "." + rs.getString("foreign_table"));
                    fkForeignCols.computeIfAbsent(name, k -> new ArrayList<>()).add(rs.getString("foreign_column"));
                }
            }
        }

        for (String constraintName : fkLocalCols.keySet()) {
            colDefs.add("    CONSTRAINT " + constraintName
                    + " FOREIGN KEY (" + String.join(", ", fkLocalCols.get(constraintName)) + ")"
                    + " REFERENCES " + fkForeignTable.get(constraintName)
                    + " (" + String.join(", ", fkForeignCols.get(constraintName)) + ")");
        }

        return "CREATE TABLE " + schema + "." + tableName + " (\n"
                + String.join(",\n", colDefs)
                + "\n);";
    }
}
