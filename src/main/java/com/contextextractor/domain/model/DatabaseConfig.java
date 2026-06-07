package com.contextextractor.domain.model;

public record DatabaseConfig(String host, int port, String database, String username, String password, String schema) {

    public String jdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }
}
