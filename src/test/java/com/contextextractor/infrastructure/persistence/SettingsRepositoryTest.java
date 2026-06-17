package com.contextextractor.infrastructure.persistence;

import com.contextextractor.domain.model.AppSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SettingsRepositoryTest {

    @Test
    @DisplayName("Returns default settings when no settings file exists")
    void returnsDefaultsWhenNoFileExists() {
        SettingsRepository repository = new SettingsRepository();

        AppSettings settings = repository.load();

        assertNotNull(settings);
        assertNotNull(settings.excludedPatterns());
    }

    @Test
    @DisplayName("Default maxXmlSizeMb is 6")
    void defaultMaxXmlSizeIs6() {
        AppSettings defaults = AppSettings.defaults();

        assertEquals(6, defaults.maxXmlSizeMb());
    }

    @Test
    @DisplayName("Default darkMode is false")
    void defaultDarkModeIsFalse() {
        AppSettings defaults = AppSettings.defaults();

        assertFalse(defaults.darkMode());
    }

    @Test
    @DisplayName("Default excludedPatterns contains node_modules and .git")
    void defaultExcludedPatternsContainExpectedEntries() {
        AppSettings defaults = AppSettings.defaults();

        assertTrue(defaults.excludedPatterns().contains("node_modules"));
        assertTrue(defaults.excludedPatterns().contains(".git"));
    }

    @Test
    @DisplayName("Default excludedPatterns contains common binary extensions")
    void defaultExcludedPatternsContainBinaryExtensions() {
        AppSettings defaults = AppSettings.defaults();

        assertTrue(defaults.excludedPatterns().contains(".class"));
        assertTrue(defaults.excludedPatterns().contains(".jar"));
        assertTrue(defaults.excludedPatterns().contains(".exe"));
        assertTrue(defaults.excludedPatterns().contains(".png"));
    }

    @Test
    @DisplayName("Default outputDirectory points to ~/.context-extractor/outputs")
    void defaultOutputDirectoryIsCorrect() {
        AppSettings defaults = AppSettings.defaults();
        String home = System.getProperty("user.home");

        assertEquals(home + "/.context-extractor/outputs", defaults.outputDirectory());
    }

    @Test
    @DisplayName("Default presetsDirectory points to ~/.context-extractor/presets")
    void defaultPresetsDirectoryIsCorrect() {
        AppSettings defaults = AppSettings.defaults();
        String home = System.getProperty("user.home");

        assertEquals(home + "/.context-extractor/presets", defaults.presetsDirectory());
    }

    @Test
    @DisplayName("AppSettings record preserves all field values through construction")
    void recordPreservesAllFields() {
        List<String> patterns = List.of(".tmp", ".log");
        AppSettings settings = new AppSettings(10, patterns, "/out", "/presets", true);

        assertEquals(10, settings.maxXmlSizeMb());
        assertEquals(patterns, settings.excludedPatterns());
        assertEquals("/out", settings.outputDirectory());
        assertEquals("/presets", settings.presetsDirectory());
        assertTrue(settings.darkMode());
    }

    @Test
    @DisplayName("VERSION constant is a non-blank semantic version string")
    void versionConstantIsNonBlank() {
        assertNotNull(AppSettings.VERSION);
        assertFalse(AppSettings.VERSION.isBlank());
        assertTrue(AppSettings.VERSION.matches("\\d+\\.\\d+\\.\\d+"));
    }

    @Test
    @DisplayName("Settings repository can be instantiated without errors")
    void repositoryInstantiatesSuccessfully() {
        SettingsRepository repository = new SettingsRepository();

        assertNotNull(repository);
    }
}
