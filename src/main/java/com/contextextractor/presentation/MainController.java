package com.contextextractor.presentation;

import com.contextextractor.application.GenerateContextUseCase;
import com.contextextractor.application.LoadPresetUseCase;
import com.contextextractor.domain.model.AgentConfig;
import com.contextextractor.domain.model.AppSettings;
import com.contextextractor.domain.model.DatabaseConfig;
import com.contextextractor.domain.model.FileSource;
import com.contextextractor.domain.model.FileSourceConfig;
import com.contextextractor.domain.model.FileSourceType;
import com.contextextractor.domain.model.Preset;
import com.contextextractor.domain.model.ScannedFile;
import com.contextextractor.domain.model.TableConfig;
import com.contextextractor.infrastructure.database.PostgresInspector;
import com.contextextractor.infrastructure.export.XmlContextExporter;
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
import javafx.scene.Scene;
import javafx.scene.control.ChoiceBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
    private Scene scene;

    private final Node[] cachedStepNodes = new Node[5];
    private final BaseStepController[] cachedStepControllers = new BaseStepController[5];

    private AgentConfig agentConfig;
    private FileSourceConfig fileSourceConfig;
    private DatabaseConfig databaseConfig;
    private List<TableConfig> tableConfigs;
    private String additionalContext = "";
    private String outputFileName = "";
    private String activePresetName;
    private boolean suppressPresetAction;

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
            if (Objects.isNull(cachedStepNodes[step])) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(STEP_FXML_PATHS[step]));
                cachedStepNodes[step] = loader.load();
                cachedStepControllers[step] = loader.getController();
                cachedStepControllers[step].setMainController(this);
            }
            cachedStepControllers[step].onNavigatedTo(step);
            contentPane.getChildren().setAll(cachedStepNodes[step]);
            stepperSidebar.setActiveStep(step);
            currentStep = step;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load step: " + STEP_FXML_PATHS[step], e);
        }
    }

    public void clearStepCache() {
        Arrays.fill(cachedStepNodes, null);
        Arrays.fill(cachedStepControllers, null);
    }

    /** Clears all user-entered state across every step, resets the stepper, and navigates to step 0. */
    public void resetAllParameters() {
        agentConfig = null;
        fileSourceConfig = null;
        databaseConfig = null;
        tableConfigs = null;
        additionalContext = "";
        outputFileName = "";
        activePresetName = null;
        suppressPresetAction = true;
        presetComboBox.setValue(null);
        suppressPresetAction = false;
        clearStepCache();
        stepperSidebar.resetAll();
        navigateTo(0);
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

    /**
     * Persists the given settings to disk and updates the in-memory property
     * so that all bound listeners (theme, export, etc.) react immediately.
     */
    public void saveSettings(AppSettings updated) {
        new SettingsRepository().save(updated);
        settings.set(updated);
    }

    /**
     * Factory method that wires infrastructure implementations into the generation use case.
     * Keeps presentation-layer controllers free from direct infrastructure imports.
     */
    public GenerateContextUseCase createGenerateContextUseCase() {
        return new GenerateContextUseCase(new PostgresInspector(), new XmlContextExporter());
    }

    public void setScene(Scene scene) {
        this.scene = scene;
        settings.addListener((obs, oldVal, newVal) -> {
            if (Objects.nonNull(oldVal) && oldVal.darkMode() != newVal.darkMode()) {
                ThemeManager.apply(scene, newVal.darkMode());
            }
        });
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public PresetRepository presetRepository() {
        String dir = settings.get().presetsDirectory();
        if (Objects.isNull(dir) || dir.isBlank()) dir = AppSettings.defaults().presetsDirectory();
        return new PresetRepository(Path.of(dir));
    }

    public void refreshPresetList() {
        suppressPresetAction = true;
        List<String> names = new LoadPresetUseCase(presetRepository())
                .loadAll().stream().map(Preset::name).toList();
        presetComboBox.getItems().setAll(names);
        if (Objects.nonNull(activePresetName) && names.contains(activePresetName)) {
            presetComboBox.setValue(activePresetName);
        }
        suppressPresetAction = false;
    }

    @FXML
    private void onSettingsClicked() {
        showSettings();
    }

    @FXML
    private void onPresetSelected() {
        if (suppressPresetAction) return;
        String name = presetComboBox.getValue();
        if (Objects.isNull(name)) return;
        activePresetName = name;
        new LoadPresetUseCase(presetRepository()).load(name).ifPresent(this::applyPreset);
    }

    private void applyPreset(Preset preset) {
        record PresetLoadResult(AgentConfig agentConfig, FileSourceConfig fileSourceConfig) {}

        Task<PresetLoadResult> task = new Task<>() {
            @Override
            protected PresetLoadResult call() throws Exception {
                AgentConfig ac = null;
                if (Objects.nonNull(preset.agentFilePath()) && !preset.agentFilePath().isBlank()) {
                    Path p = Path.of(preset.agentFilePath());
                    if (Files.isRegularFile(p)) {
                        ac = new AgentConfig(p, Files.readString(p));
                    }
                }

                FileSourceConfig fsc = null;
                if (Objects.nonNull(preset.fileSources()) && !preset.fileSources().isEmpty()) {
                    AppSettings s = Objects.nonNull(preset.settings()) ? preset.settings() : settings.get();
                    RecursiveFileScanner scanner = new RecursiveFileScanner(s);
                    List<FileSource> sources = new ArrayList<>();
                    for (Preset.PresetSource ps : preset.fileSources()) {
                        try {
                            FileSourceType type = FileSourceType.valueOf(ps.type());
                            Path p = Path.of(ps.path()).toAbsolutePath().normalize();
                            if (type == FileSourceType.DIRECTORY && Files.isDirectory(p)) {
                                sources.add(new FileSource(type, p, scanner.scan(p), Set.of()));
                            } else if (type == FileSourceType.FILE && Files.isRegularFile(p)) {
                                sources.add(new FileSource(type, p, scanner.scanSingle(p), Set.of()));
                            } else if (type == FileSourceType.GIT_DIFF && Files.isDirectory(p)) {
                                String modeStr = Objects.nonNull(ps.mode()) ? ps.mode() : "ALL_CHANGES";
                                ScannedFile placeholder = new ScannedFile(modeStr + "||" + p, "");
                                sources.add(new FileSource(type, p, List.of(placeholder), Set.of()));
                            }
                        } catch (Exception ignored) {}
                    }
                    if (!sources.isEmpty()) fsc = new FileSourceConfig(sources);
                }

                return new PresetLoadResult(ac, fsc);
            }
        };

        task.setOnSucceeded(e -> {
            PresetLoadResult result = task.getValue();
            agentConfig = result.agentConfig();
            fileSourceConfig = result.fileSourceConfig();
            databaseConfig = preset.databaseConfig();
            tableConfigs = preset.tableConfigs();
            additionalContext = Objects.nonNull(preset.additionalContext()) ? preset.additionalContext() : "";
            outputFileName = Objects.nonNull(preset.outputFileName()) ? preset.outputFileName() : "";
            if (Objects.nonNull(preset.settings())) settings.set(preset.settings());
            clearStepCache();
            navigateTo(0);
        });

        task.setOnFailed(e -> {
            databaseConfig = preset.databaseConfig();
            tableConfigs = preset.tableConfigs();
            additionalContext = Objects.nonNull(preset.additionalContext()) ? preset.additionalContext() : "";
            outputFileName = Objects.nonNull(preset.outputFileName()) ? preset.outputFileName() : "";
            clearStepCache();
            navigateTo(0);
        });

        Thread thread = new Thread(task, "load-preset");
        thread.setDaemon(true);
        thread.start();
    }

    public void setAgentConfig(AgentConfig config) { agentConfig = config; }
    public AgentConfig getAgentConfig() { return agentConfig; }

    public void setFileSourceConfig(FileSourceConfig config) { fileSourceConfig = config; }
    public FileSourceConfig getFileSourceConfig() { return fileSourceConfig; }

    public void setDatabaseConfig(DatabaseConfig config) { databaseConfig = config; }
    public DatabaseConfig getDatabaseConfig() { return databaseConfig; }

    public void setTableConfigs(List<TableConfig> configs) { tableConfigs = configs; }
    public List<TableConfig> getTableConfigs() { return tableConfigs; }

    public void setAdditionalContext(String text) { additionalContext = text; }
    public String getAdditionalContext() { return additionalContext; }

    public void setOutputFileName(String name) { outputFileName = name; }
    public String getOutputFileName() { return outputFileName; }

    public ObjectProperty<AppSettings> settingsProperty() { return settings; }
    public AppSettings getSettings() { return settings.get(); }
}
