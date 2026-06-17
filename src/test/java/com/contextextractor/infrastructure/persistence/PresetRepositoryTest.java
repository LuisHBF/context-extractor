package com.contextextractor.infrastructure.persistence;

import com.contextextractor.domain.model.AppSettings;
import com.contextextractor.domain.model.DatabaseConfig;
import com.contextextractor.domain.model.Preset;
import com.contextextractor.domain.model.TableConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PresetRepositoryTest {

    private Path presetsDir;
    private PresetRepository repository;

    @BeforeEach
    void setUp() throws IOException {
        presetsDir = Files.createTempDirectory("presets-test");
        repository = new PresetRepository(presetsDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(presetsDir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> p.toFile().delete());
    }

    @Test
    @DisplayName("Saves a preset as a JSON file in the configured presets directory")
    void savesPresetAsJsonFile() {
        Preset preset = buildPreset("my-project");

        repository.save(preset);

        Path expectedFile = presetsDir.resolve("my-project.json");
        assertTrue(Files.exists(expectedFile));
    }

    @Test
    @DisplayName("Loads a previously saved preset by name")
    void loadsPresetByName() {
        Preset preset = buildPreset("backend");
        repository.save(preset);

        Optional<Preset> loaded = repository.loadByName("backend");

        assertTrue(loaded.isPresent());
        assertEquals("backend", loaded.get().name());
    }

    @Test
    @DisplayName("Returns empty Optional when loading a preset name that does not exist")
    void returnsEmptyForNonExistentPreset() {
        Optional<Preset> loaded = repository.loadByName("non-existent");

        assertTrue(loaded.isEmpty());
    }

    @Test
    @DisplayName("loadAll returns all presets saved in the directory")
    void loadAllReturnsAllSavedPresets() {
        repository.save(buildPreset("alpha"));
        repository.save(buildPreset("beta"));
        repository.save(buildPreset("gamma"));

        List<Preset> all = repository.loadAll();

        assertEquals(3, all.size());
        Set<String> names = all.stream().map(Preset::name).collect(Collectors.toSet());
        assertEquals(Set.of("alpha", "beta", "gamma"), names);
    }

    @Test
    @DisplayName("loadAll returns empty list when presets directory does not exist")
    void loadAllReturnsEmptyWhenDirectoryMissing() throws IOException {
        Path nonExistent = presetsDir.resolve("missing-subdir");
        PresetRepository repo = new PresetRepository(nonExistent);

        List<Preset> result = repo.loadAll();

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Overwrites an existing preset file when saving with the same name")
    void overwritesExistingPresetOnSave() {
        Preset original = buildPreset("config");
        repository.save(original);

        Preset updated = new Preset("config", "/new/agent.md", List.of(),
                null, "", List.of(), "updated context", AppSettings.defaults(), "output");
        repository.save(updated);

        Optional<Preset> loaded = repository.loadByName("config");
        assertTrue(loaded.isPresent());
        assertEquals("updated context", loaded.get().additionalContext());
    }

    @Test
    @DisplayName("Saved preset JSON can be deserialized back into an equal Preset record")
    void savedPresetRoundTripsCorrectly() {
        DatabaseConfig dbConfig = new DatabaseConfig("localhost", 5432, "mydb", "admin", "secret", "public");
        TableConfig table = new TableConfig("users", true, true, 10, "status = 'active'", "id DESC");
        Preset preset = new Preset(
                "full-preset",
                "/path/to/agent.md",
                List.of(new Preset.PresetSource("DIRECTORY", "/src/main", null)),
                dbConfig,
                "public",
                List.of(table),
                "Some additional context",
                AppSettings.defaults(),
                "my-output");

        repository.save(preset);
        Optional<Preset> loaded = repository.loadByName("full-preset");

        assertTrue(loaded.isPresent());
        Preset result = loaded.get();
        assertEquals(preset.name(), result.name());
        assertEquals(preset.agentFilePath(), result.agentFilePath());
        assertEquals(preset.additionalContext(), result.additionalContext());
        assertEquals(preset.selectedSchema(), result.selectedSchema());
        assertEquals(preset.outputFileName(), result.outputFileName());
        assertEquals(preset.tableConfigs().size(), result.tableConfigs().size());
        assertEquals(preset.tableConfigs().get(0).tableName(), result.tableConfigs().get(0).tableName());
        assertEquals(preset.tableConfigs().get(0).whereClause(), result.tableConfigs().get(0).whereClause());
    }

    @Test
    @DisplayName("Constructor rejects null directory")
    void rejectsNullDirectory() {
        assertThrows(NullPointerException.class, () -> new PresetRepository(null));
    }

    @Test
    @DisplayName("save rejects null preset")
    void rejectsNullPreset() {
        assertThrows(NullPointerException.class, () -> repository.save(null));
    }

    @Test
    @DisplayName("loadAll skips malformed JSON files without throwing")
    void loadAllSkipsMalformedJson() throws IOException {
        repository.save(buildPreset("valid"));
        Files.writeString(presetsDir.resolve("broken.json"), "{{{invalid json");

        List<Preset> result = repository.loadAll();

        assertEquals(1, result.size());
        assertEquals("valid", result.get(0).name());
    }

    private Preset buildPreset(String name) {
        return new Preset(name, "/agent.md", List.of(), null, "", List.of(), "context", AppSettings.defaults(), "output");
    }
}
