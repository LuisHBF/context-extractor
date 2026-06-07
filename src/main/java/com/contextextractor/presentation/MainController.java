package com.contextextractor.presentation;

import com.contextextractor.application.LoadPresetUseCase;
import com.contextextractor.domain.model.AgentConfig;
import com.contextextractor.domain.model.AppSettings;
import com.contextextractor.domain.model.DatabaseConfig;
import com.contextextractor.domain.model.DirectoryConfig;
import com.contextextractor.domain.model.Preset;
import com.contextextractor.domain.model.ScannedFile;
import com.contextextractor.domain.model.TableConfig;
import com.contextextractor.infrastructure.filesystem.RecursiveFileScanner;
import com.contextextractor.infrastructure.persistence.PresetRepository;
import com.contextextractor.infrastructure.persistence.SettingsRepository;
import com.contextextractor.presentation.components.StepperSidebar;
import com.contextextractor.presentation.settings.SettingsController;
import com.contextextractor.presentation.steps.BaseStepController;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class MainController {

    private static final String[] STEP_FXML_PATHS = {
            "/fxml/steps/agent-step.fxml",
            "/fxml/steps/directory-step.fxml",
            "/fxml/steps/database-step.fxml",
            "/fxml/steps/additional-context-step.fxml",
            "/fxml/steps/review-step.fxml"
    };

    @FXML private BorderPane rootPane;
    @FXML private StackPane contentPane;
    @FXML private ChoiceBox<String> presetComboBox;

    private StepperSidebar stepperSidebar;
    private int currentStep = 0;
    private final ObjectProperty<AppSettings> settings = new SimpleObjectProperty<>();

    private AgentConfig agentConfig;
    private DirectoryConfig directoryConfig;
    private DatabaseConfig databaseConfig;
    private List<TableConfig> tableConfigs;
    private String additionalContext = "";

    @FXML
    public void initialize() {
        settings.set(new SettingsRepository().load());

        stepperSidebar = new StepperSidebar();
        stepperSidebar.setOnStepClicked(this::navigateTo);
        stepperSidebar.setOnSettingsClicked(this::showSettings);
        rootPane.setLeft(stepperSidebar);

        refreshPresetList();
        navigateTo(0);
    }

    public void nextStep() {
        if (currentStep < 4) {
            stepperSidebar.markCompleted(currentStep);
            navigateTo(currentStep + 1);
        }
    }

    public void previousStep() {
        if (currentStep > 0) {
            navigateTo(currentStep - 1);
        }
    }

    public void navigateTo(int step) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(STEP_FXML_PATHS[step]));
            Node content = loader.load();
            BaseStepController controller = loader.getController();
            controller.setMainController(this);
            controller.onNavigatedTo(step);
            contentPane.getChildren().setAll(content);
            stepperSidebar.setActiveStep(step);
            currentStep = step;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load step: " + STEP_FXML_PATHS[step], e);
        }
    }

    public void markStepCompleted(int step) {
        stepperSidebar.markCompleted(step);
    }

    public void showSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/settings/settings.fxml"));
            Node content = loader.load();
            SettingsController controller = loader.getController();
            controller.setMainController(this);
            controller.onNavigatedToSettings();
            contentPane.getChildren().setAll(content);
            stepperSidebar.setActiveStep(-1);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load settings", e);
        }
    }

    public void updateSettings(AppSettings updated) {
        settings.set(updated);
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public PresetRepository presetRepository() {
        String dir = settings.get().presetsDirectory();
        if (dir == null || dir.isBlank()) dir = AppSettings.defaults().presetsDirectory();
        return new PresetRepository(Path.of(dir));
    }

    public void refreshPresetList() {
        List<String> names = new LoadPresetUseCase(presetRepository())
                .loadAll().stream().map(Preset::name).toList();
        presetComboBox.getItems().setAll(names);
    }

    @FXML
    private void onSettingsClicked() {
        showSettings();
    }

    @FXML
    private void onPresetSelected() {
        String name = presetComboBox.getValue();
        if (name == null) return;
        presetComboBox.setValue(null);
        new LoadPresetUseCase(presetRepository()).load(name).ifPresent(this::applyPreset);
    }

    private void applyPreset(Preset preset) {
        record PresetLoadResult(AgentConfig agentConfig, DirectoryConfig directoryConfig) {}

        Task<PresetLoadResult> task = new Task<>() {
            @Override
            protected PresetLoadResult call() throws Exception {
                AgentConfig ac = null;
                if (preset.agentFilePath() != null && !preset.agentFilePath().isBlank()) {
                    Path p = Path.of(preset.agentFilePath());
                    if (Files.isRegularFile(p)) {
                        ac = new AgentConfig(p, Files.readString(p));
                    }
                }

                DirectoryConfig dc = null;
                if (preset.directoryPath() != null && !preset.directoryPath().isBlank()) {
                    Path p = Path.of(preset.directoryPath());
                    if (Files.isDirectory(p)) {
                        AppSettings s = preset.settings() != null ? preset.settings() : settings.get();
                        List<ScannedFile> files = new RecursiveFileScanner(s).scan(p);
                        dc = new DirectoryConfig(p, files);
                    }
                }

                return new PresetLoadResult(ac, dc);
            }
        };

        task.setOnSucceeded(e -> {
            PresetLoadResult result = task.getValue();
            agentConfig = result.agentConfig();
            directoryConfig = result.directoryConfig();
            databaseConfig = preset.databaseConfig();
            tableConfigs = preset.tableConfigs();
            additionalContext = preset.additionalContext() != null ? preset.additionalContext() : "";
            if (preset.settings() != null) settings.set(preset.settings());
            navigateTo(0);
        });

        task.setOnFailed(e -> {
            databaseConfig = preset.databaseConfig();
            tableConfigs = preset.tableConfigs();
            additionalContext = preset.additionalContext() != null ? preset.additionalContext() : "";
            navigateTo(0);
        });

        Thread thread = new Thread(task, "load-preset");
        thread.setDaemon(true);
        thread.start();
    }

    public void setAgentConfig(AgentConfig config) { agentConfig = config; }
    public AgentConfig getAgentConfig() { return agentConfig; }

    public void setDirectoryConfig(DirectoryConfig config) { directoryConfig = config; }
    public DirectoryConfig getDirectoryConfig() { return directoryConfig; }

    public void setDatabaseConfig(DatabaseConfig config) { databaseConfig = config; }
    public DatabaseConfig getDatabaseConfig() { return databaseConfig; }

    public void setTableConfigs(List<TableConfig> configs) { tableConfigs = configs; }
    public List<TableConfig> getTableConfigs() { return tableConfigs; }

    public void setAdditionalContext(String text) { additionalContext = text; }
    public String getAdditionalContext() { return additionalContext; }

    public ObjectProperty<AppSettings> settingsProperty() { return settings; }
    public AppSettings getSettings() { return settings.get(); }
}
