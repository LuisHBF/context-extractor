package com.contextextractor.infrastructure.persistence;

import com.contextextractor.domain.model.AppSettings;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

/**
 * Persists and loads {@link AppSettings} as a JSON file under the application data directory.
 * Missing or corrupted files fall back to {@link AppSettings#defaults()}.
 */
public class SettingsRepository {

    private static final String APP_DATA_DIR =
            System.getProperty("user.home") + "/.context-extractor";
    private static final String SETTINGS_FILE = APP_DATA_DIR + "/settings.json";

    private final ObjectMapper mapper = new ObjectMapper()
            .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public AppSettings load() {
        File file = new File(SETTINGS_FILE);
        if (!file.exists()) {
            return AppSettings.defaults();
        }
        try {
            AppSettings loaded = mapper.readValue(file, AppSettings.class);
            return ensureDefaults(loaded);
        } catch (IOException e) {
            return AppSettings.defaults();
        }
    }

    private AppSettings ensureDefaults(AppSettings s) {
        AppSettings d = AppSettings.defaults();
        String out = (Objects.nonNull(s.outputDirectory()) && !s.outputDirectory().isBlank()) ? s.outputDirectory() : d.outputDirectory();
        String presets = (Objects.nonNull(s.presetsDirectory()) && !s.presetsDirectory().isBlank()) ? s.presetsDirectory() : d.presetsDirectory();
        return new AppSettings(s.maxXmlSizeMb(), s.excludedPatterns(), out, presets, s.darkMode());
    }

    public void save(AppSettings settings) {
        try {
            new File(APP_DATA_DIR).mkdirs();
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(SETTINGS_FILE), settings);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save settings", e);
        }
    }
}
