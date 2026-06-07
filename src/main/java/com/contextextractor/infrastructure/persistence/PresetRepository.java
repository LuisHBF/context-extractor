package com.contextextractor.infrastructure.persistence;

import com.contextextractor.domain.model.Preset;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PresetRepository {

    @JsonIgnoreProperties({"jdbcUrl"})
    abstract static class DatabaseConfigMixin {}

    private final Path presetsDir;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .addMixIn(com.contextextractor.domain.model.DatabaseConfig.class, DatabaseConfigMixin.class);

    public PresetRepository(Path directory) {
        this.presetsDir = directory;
    }

    public void save(Preset preset) {
        try {
            Files.createDirectories(presetsDir);
            Path file = presetsDir.resolve(sanitize(preset.name()) + ".json");
            mapper.writeValue(file.toFile(), preset);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save preset: " + preset.name(), e);
        }
    }

    public List<Preset> loadAll() {
        if (!Files.exists(presetsDir)) return List.of();
        List<Preset> presets = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(presetsDir, "*.json")) {
            for (Path file : stream) {
                try {
                    presets.add(mapper.readValue(file.toFile(), Preset.class));
                } catch (IOException ignored) {
                }
            }
        } catch (IOException e) {
            return List.of();
        }
        return presets;
    }

    public Optional<Preset> loadByName(String name) {
        Path file = presetsDir.resolve(sanitize(name) + ".json");
        if (!Files.exists(file)) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(file.toFile(), Preset.class));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
