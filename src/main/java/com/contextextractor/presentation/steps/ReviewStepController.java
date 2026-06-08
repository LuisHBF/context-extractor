package com.contextextractor.presentation.steps;

import com.contextextractor.application.GenerateContextUseCase;
import com.contextextractor.application.SavePresetUseCase;
import com.contextextractor.domain.model.AgentConfig;
import com.contextextractor.domain.model.AppSettings;
import com.contextextractor.domain.model.ContextPayload;
import com.contextextractor.domain.model.DatabaseConfig;
import com.contextextractor.domain.model.FileSource;
import com.contextextractor.domain.model.FileSourceConfig;
import com.contextextractor.domain.model.Preset;
import com.contextextractor.domain.model.ScannedFile;
import com.contextextractor.domain.model.TableConfig;
import com.contextextractor.infrastructure.database.PostgresInspector;
import com.contextextractor.infrastructure.export.XmlContextExporter;
import com.contextextractor.infrastructure.persistence.PresetRepository;
import com.contextextractor.presentation.components.ToastNotification;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ReviewStepController extends BaseStepController {

    @FXML private Label agentReviewLabel;
    @FXML private Label directoryReviewLabel;
    @FXML private Label databaseReviewLabel;
    @FXML private Label additionalContextReviewLabel;
    @FXML private Label outputReviewLabel;

    @FXML private Button generateButton;
    @FXML private VBox progressSection;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressStatusLabel;

    @FXML private VBox resultPanel;
    @FXML private Label resultFilesLabel;

    @FXML private VBox errorPanel;
    @FXML private Label errorMessageLabel;

    @FXML private HBox savePresetPanel;
    @FXML private TextField presetNameField;
    @FXML private Label presetSaveStatusLabel;
    @FXML private TextField outputFileNameField;

    private Path lastOutputDirectory;

    @Override
    public void onNavigatedTo(int stepIndex) {
        super.onNavigatedTo(stepIndex);
        populateReviewCards();
    }

    private void populateReviewCards() {
        AgentConfig ac = mainController.getAgentConfig();
        setReviewCard(agentReviewLabel,
                ac != null ? ac.filePath().toString() : null,
                "No agent selected — the AI will operate without a system prompt.");

        FileSourceConfig fsc = mainController.getFileSourceConfig();
        if (fsc != null && !fsc.sources().isEmpty()) {
            int total = fsc.allFiles().size();
            int srcs = fsc.sources().size();
            setReviewCard(directoryReviewLabel,
                    srcs + (srcs == 1 ? " source" : " sources") + ", " + total + (total == 1 ? " file" : " files"),
                    null);
        } else {
            setReviewCard(directoryReviewLabel, null,
                    "No files selected — only database and context will be included.");
        }

        DatabaseConfig dbc = mainController.getDatabaseConfig();
        if (dbc != null && !dbc.host().isBlank()) {
            StringBuilder sb = new StringBuilder();
            sb.append(dbc.host()).append(":").append(dbc.port()).append("/").append(dbc.database());
            if (!dbc.schema().isBlank()) sb.append(" — schema: ").append(dbc.schema());
            List<TableConfig> tables = mainController.getTableConfigs();
            if (tables != null && !tables.isEmpty()) {
                sb.append("\n").append(tables.size()).append(" table(s): ");
                sb.append(tables.stream().map(TableConfig::tableName).collect(Collectors.joining(", ")));
            }
            setReviewCard(databaseReviewLabel, sb.toString(), null);
        } else {
            setReviewCard(databaseReviewLabel, null,
                    "No database configured — only files and context will be included.");
        }

        String ctx = mainController.getAdditionalContext();
        setReviewCard(additionalContextReviewLabel,
                (ctx != null && !ctx.isBlank()) ? (ctx.length() > 200 ? ctx.substring(0, 200) + "..." : ctx) : null,
                "No additional context provided.");

        AppSettings settings = mainController.getSettings();
        String configuredDir = settings.outputDirectory();
        String outputPath = (configuredDir != null && !configuredDir.isBlank())
                ? configuredDir
                : System.getProperty("user.home") + "/Downloads";
        outputReviewLabel.setText("Max file size: " + settings.maxXmlSizeMb() + " MB\nOutput: " + outputPath);
    }

    private void setReviewCard(Label label, String content, String emptyMessage) {
        label.getStyleClass().removeAll("review-empty-state");
        if (content != null) {
            label.setText(content);
        } else {
            label.setText(emptyMessage);
            label.getStyleClass().add("review-empty-state");
        }
    }

    @FXML
    private void onGenerate() {
        ContextPayload payload = buildPayload();

        generateButton.setDisable(true);
        progressSection.setVisible(true);
        progressSection.setManaged(true);
        resultPanel.setVisible(false);
        resultPanel.setManaged(false);
        errorPanel.setVisible(false);
        errorPanel.setManaged(false);

        GenerateContextUseCase useCase = new GenerateContextUseCase(new PostgresInspector(), new XmlContextExporter());

        Task<GenerateContextUseCase.GenerationResult> task = new Task<>() {
            @Override
            protected GenerateContextUseCase.GenerationResult call() throws Exception {
                return useCase.execute(payload, progress -> {
                    updateProgress((long) (progress.progress() * 100), 100L);
                    updateMessage(progress.message());
                });
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());
        progressStatusLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(e -> {
            generateButton.setDisable(false);
            progressBar.progressProperty().unbind();
            progressStatusLabel.textProperty().unbind();

            List<Path> files = task.getValue().outputFiles();
            if (!files.isEmpty()) lastOutputDirectory = files.get(0).getParent();
            resultFilesLabel.setText(files.stream().map(Path::toString).collect(Collectors.joining("\n")));
            resultPanel.setVisible(true);
            resultPanel.setManaged(true);
        });

        task.setOnFailed(e -> {
            generateButton.setDisable(false);
            progressBar.progressProperty().unbind();
            progressStatusLabel.textProperty().unbind();

            Throwable ex = task.getException();
            errorMessageLabel.setText(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            errorPanel.setVisible(true);
            errorPanel.setManaged(true);
        });

        Thread thread = new Thread(task, "generate-context");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onOpenOutputFolder() {
        if (lastOutputDirectory == null) return;
        try {
            Desktop.getDesktop().open(lastOutputDirectory.toFile());
        } catch (IOException ignored) {
        }
    }

    @FXML
    private void onToggleSavePreset() {
        boolean show = !savePresetPanel.isVisible();
        savePresetPanel.setVisible(show);
        savePresetPanel.setManaged(show);
        if (show) {
            FileSourceConfig fsc = mainController.getFileSourceConfig();
            String suggestion = (fsc != null && !fsc.sources().isEmpty())
                    ? fsc.sources().get(0).path().getFileName().toString() : "";
            presetNameField.setText(suggestion);
            presetSaveStatusLabel.setText("");
        }
    }

    @FXML
    private void onSavePreset() {
        String name = presetNameField.getText().trim();
        if (name.isBlank()) {
            presetSaveStatusLabel.setText("Please enter a preset name.");
            return;
        }
        try {
            new SavePresetUseCase(mainController.presetRepository()).save(buildPreset(name));
            presetSaveStatusLabel.setText("Preset saved \u2713");
            mainController.refreshPresetList();
            ToastNotification.showSuccess(generateButton, "Preset saved \u2713");
        } catch (Exception ex) {
            presetSaveStatusLabel.setText("Failed to save: " + ex.getMessage());
        }
    }

    private ContextPayload buildPayload() {
        String agentContent = "";
        AgentConfig ac = mainController.getAgentConfig();
        if (ac != null) agentContent = ac.content();

        Map<String, String> files = new LinkedHashMap<>();
        FileSourceConfig fsc = mainController.getFileSourceConfig();
        if (fsc != null) {
            for (ScannedFile f : fsc.allFiles()) {
                files.put(f.relativePath(), f.content());
            }
        }

        return new ContextPayload(
                agentContent,
                files,
                mainController.getDatabaseConfig(),
                mainController.getTableConfigs() != null ? mainController.getTableConfigs() : List.of(),
                Map.of(),
                Map.of(),
                mainController.getAdditionalContext() != null ? mainController.getAdditionalContext() : "",
                mainController.getSettings(),
                null,
                outputFileNameField.getText().trim());
    }

    private Preset buildPreset(String name) {
        AgentConfig ac = mainController.getAgentConfig();
        FileSourceConfig fsc = mainController.getFileSourceConfig();
        DatabaseConfig dbc = mainController.getDatabaseConfig();

        List<Preset.PresetSource> presetSources = fsc != null
                ? fsc.sources().stream()
                        .map(s -> new Preset.PresetSource(s.type().name(), s.path().toString()))
                        .toList()
                : List.of();

        return new Preset(
                name,
                ac != null ? ac.filePath().toString() : "",
                presetSources,
                dbc,
                dbc != null ? dbc.schema() : "",
                mainController.getTableConfigs() != null ? mainController.getTableConfigs() : List.of(),
                mainController.getAdditionalContext() != null ? mainController.getAdditionalContext() : "",
                mainController.getSettings(),
                outputFileNameField.getText().trim());
    }
}
