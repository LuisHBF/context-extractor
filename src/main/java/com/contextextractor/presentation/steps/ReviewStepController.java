package com.contextextractor.presentation.steps;

import com.contextextractor.application.GenerateContextUseCase;
import com.contextextractor.application.SavePresetUseCase;
import com.contextextractor.domain.model.AgentConfig;
import com.contextextractor.domain.model.AppSettings;
import com.contextextractor.domain.model.ContextPayload;
import com.contextextractor.domain.model.DatabaseConfig;
import com.contextextractor.domain.model.FileSource;
import com.contextextractor.domain.model.FileSourceConfig;
import com.contextextractor.domain.model.FileSourceType;
import com.contextextractor.domain.model.Preset;
import com.contextextractor.domain.model.ScannedFile;
import com.contextextractor.domain.model.TableConfig;
import com.contextextractor.infrastructure.persistence.PresetRepository;
import com.contextextractor.presentation.components.ToastNotification;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ReviewStepController extends BaseStepController {

    @FXML private Label agentReviewLabel;
    @FXML private Label directoryReviewLabel;
    @FXML private Label databaseReviewLabel;
    @FXML private VBox databaseCardContent;
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

    private HBox databaseExpandToggle;
    private Label databaseExpandIcon;
    private VBox databaseDetailBox;
    private boolean databaseDetailExpanded;

    private Path lastOutputDirectory;
    private boolean initialized;

    @Override
    public void onNavigatedTo(int stepIndex) {
        super.onNavigatedTo(stepIndex);
        if (!initialized) {
            initialized = true;
            outputFileNameField.textProperty().addListener(
                    (obs, old, val) -> mainController.setOutputFileName(val.trim()));
            String savedName = mainController.getOutputFileName();
            if (Objects.nonNull(savedName) && !savedName.isBlank()) {
                outputFileNameField.setText(savedName);
            }
        }
        populateReviewCards();
    }

    private void populateReviewCards() {
        AgentConfig agentConfig = mainController.getAgentConfig();
        setReviewCard(agentReviewLabel,
                Objects.nonNull(agentConfig) ? agentConfig.filePath().toString() : null,
                "No agent selected — the AI will operate without a system prompt.");

        FileSourceConfig fileSourceConfig = mainController.getFileSourceConfig();
        String fileSummary = (Objects.nonNull(fileSourceConfig) && !fileSourceConfig.sources().isEmpty())
                ? buildFileSourceSummary(fileSourceConfig) : null;
        setReviewCard(directoryReviewLabel, fileSummary,
                "No files selected — only database and context will be included.");

        DatabaseConfig databaseConfig = mainController.getDatabaseConfig();
        String databaseSummary = (Objects.nonNull(databaseConfig) && !databaseConfig.host().isBlank())
                ? buildDatabaseSummary(databaseConfig) : null;
        if (Objects.isNull(databaseSummary)) clearDatabaseDetailSection();
        setReviewCard(databaseReviewLabel, databaseSummary,
                "No database configured — only files and context will be included.");

        String additionalContext = mainController.getAdditionalContext();
        String contextPreview = (Objects.nonNull(additionalContext) && !additionalContext.isBlank())
                ? (additionalContext.length() > 200 ? additionalContext.substring(0, 200) + "..." : additionalContext)
                : null;
        setReviewCard(additionalContextReviewLabel, contextPreview, "No additional context provided.");

        AppSettings settings = mainController.getSettings();
        outputReviewLabel.setText("Max file size: " + settings.maxXmlSizeMb() + " MB\nOutput: "
                + resolveDisplayedOutputPath(settings));
    }

    private String buildFileSourceSummary(FileSourceConfig fsc) {
        int totalFiles = fsc.allFiles().size();
        int diffSources = fsc.gitDiffSources().size();
        int sourceCount = fsc.sources().size();
        StringBuilder sb = new StringBuilder();
        sb.append(sourceCount).append(sourceCount == 1 ? " source" : " sources");
        if (totalFiles > 0) {
            sb.append(", ").append(totalFiles).append(totalFiles == 1 ? " file" : " files");
        }
        if (diffSources > 0) {
            sb.append(", ").append(diffSources).append(diffSources == 1 ? " git diff" : " git diffs");
        }
        return sb.toString();
    }

    private String buildDatabaseSummary(DatabaseConfig databaseConfig) {
        StringBuilder sb = new StringBuilder();
        sb.append(databaseConfig.host()).append(":").append(databaseConfig.port())
          .append("/").append(databaseConfig.database());
        if (!databaseConfig.schema().isBlank()) {
            sb.append(" — schema: ").append(databaseConfig.schema());
        }
        List<TableConfig> tables = mainController.getTableConfigs();
        if (Objects.nonNull(tables) && !tables.isEmpty()) {
            long ddlCount = tables.stream().filter(TableConfig::exportDdl).count();
            long dataCount = tables.stream().filter(TableConfig::exportData).count();
            sb.append("\n").append(tables.size()).append(tables.size() == 1 ? " table" : " tables").append(" selected");
            if (ddlCount > 0) sb.append(" \u00b7 ").append(ddlCount).append(" with DDL");
            if (dataCount > 0) sb.append(" \u00b7 ").append(dataCount).append(" with data");
            buildDatabaseDetailSection(tables);
        } else {
            clearDatabaseDetailSection();
        }
        return sb.toString();
    }

    private void buildDatabaseDetailSection(List<TableConfig> tables) {
        if (databaseExpandToggle == null) {
            databaseExpandIcon = new Label("\u25b6");
            databaseExpandIcon.getStyleClass().add("db-expand-icon");
            Label expandText = new Label("Show table details");
            expandText.getStyleClass().add("review-expand-text");
            databaseExpandToggle = new HBox(6, databaseExpandIcon, expandText);
            databaseExpandToggle.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            databaseExpandToggle.setCursor(Cursor.HAND);
            databaseExpandToggle.setPadding(new Insets(4, 0, 0, 0));
            databaseExpandToggle.setOnMouseClicked(e -> onToggleDatabaseDetail());
            databaseDetailBox = new VBox(4);
            databaseDetailBox.setPadding(new Insets(4, 0, 0, 8));
            databaseDetailBox.setVisible(false);
            databaseDetailBox.setManaged(false);
            databaseCardContent.getChildren().addAll(databaseExpandToggle, databaseDetailBox);
        }

        databaseExpandToggle.setVisible(true);
        databaseExpandToggle.setManaged(true);

        databaseDetailBox.getChildren().clear();
        for (TableConfig tc : tables) {
            StringBuilder detail = new StringBuilder("\u2022 ").append(tc.tableName());
            List<String> flags = new ArrayList<>();
            if (tc.exportDdl()) flags.add("DDL");
            if (tc.exportData()) flags.add(tc.rowLimit() + " rows");
            if (Objects.nonNull(tc.whereClause()) && !tc.whereClause().isBlank())
                flags.add("WHERE: " + tc.whereClause());
            if (Objects.nonNull(tc.orderByClause()) && !tc.orderByClause().isBlank())
                flags.add("ORDER BY: " + tc.orderByClause());
            if (!flags.isEmpty()) detail.append(" \u2014 ").append(String.join(", ", flags));
            Label label = new Label(detail.toString());
            label.getStyleClass().add("step-description");
            label.setWrapText(true);
            databaseDetailBox.getChildren().add(label);
        }

        if (!databaseDetailExpanded) {
            databaseDetailBox.setVisible(false);
            databaseDetailBox.setManaged(false);
        }
    }

    private void clearDatabaseDetailSection() {
        if (databaseExpandToggle != null) {
            databaseExpandToggle.setVisible(false);
            databaseExpandToggle.setManaged(false);
            databaseDetailBox.setVisible(false);
            databaseDetailBox.setManaged(false);
            databaseDetailExpanded = false;
        }
    }

    private void onToggleDatabaseDetail() {
        databaseDetailExpanded = !databaseDetailExpanded;
        databaseExpandIcon.setText(databaseDetailExpanded ? "\u25bc" : "\u25b6");
        databaseDetailBox.setVisible(databaseDetailExpanded);
        databaseDetailBox.setManaged(databaseDetailExpanded);
    }

    private String resolveDisplayedOutputPath(AppSettings settings) {
        String dir = settings.outputDirectory();
        return (Objects.nonNull(dir) && !dir.isBlank()) ? dir : System.getProperty("user.home") + "/Downloads";
    }

    private void setReviewCard(Label label, String content, String emptyMessage) {
        label.getStyleClass().removeAll("review-empty-state");
        if (Objects.nonNull(content)) {
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

        GenerateContextUseCase useCase = mainController.createGenerateContextUseCase();

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
            progressSection.setVisible(false);
            progressSection.setManaged(false);

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
            progressSection.setVisible(false);
            progressSection.setManaged(false);

            Throwable ex = task.getException();
            errorMessageLabel.setText(Objects.nonNull(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName());
            errorPanel.setVisible(true);
            errorPanel.setManaged(true);
        });

        Thread thread = new Thread(task, "generate-context");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onOpenOutputFolder() {
        if (Objects.isNull(lastOutputDirectory)) return;
        try {
            Desktop.getDesktop().open(lastOutputDirectory.toFile());
        } catch (IOException ignored) {
        }
    }

    @FXML
    private void onResetParameters() {
        mainController.resetAllParameters();
    }

    @FXML
    private void onToggleSavePreset() {
        boolean show = !savePresetPanel.isVisible();
        savePresetPanel.setVisible(show);
        savePresetPanel.setManaged(show);
        if (show) {
            FileSourceConfig fsc = mainController.getFileSourceConfig();
            String suggestion = (Objects.nonNull(fsc) && !fsc.sources().isEmpty())
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
        AgentConfig agentConfig = mainController.getAgentConfig();
        if (Objects.nonNull(agentConfig)) agentContent = agentConfig.content();

        Map<String, String> files = new LinkedHashMap<>();
        List<ScannedFile> codeChanges = new ArrayList<>();
        FileSourceConfig fsc = mainController.getFileSourceConfig();
        if (Objects.nonNull(fsc)) {
            for (ScannedFile file : fsc.allFiles()) {
                files.put(file.relativePath(), file.content());
            }
            for (FileSource source : fsc.gitDiffSources()) {
                codeChanges.addAll(source.includedFiles());
            }
        }

        return new ContextPayload(
                agentContent,
                files,
                mainController.getDatabaseConfig(),
                Objects.nonNull(mainController.getTableConfigs()) ? mainController.getTableConfigs() : List.of(),
                Map.of(),
                Map.of(),
                Objects.nonNull(mainController.getAdditionalContext()) ? mainController.getAdditionalContext() : "",
                mainController.getSettings(),
                null,
                outputFileNameField.getText().trim(),
                codeChanges);
    }

    private Preset buildPreset(String name) {
        AgentConfig agentConfig = mainController.getAgentConfig();
        FileSourceConfig fsc = mainController.getFileSourceConfig();
        DatabaseConfig databaseConfig = mainController.getDatabaseConfig();

        List<Preset.PresetSource> presetSources = Objects.nonNull(fsc)
                ? fsc.sources().stream()
                        .map(s -> {
                            String mode = s.type() == FileSourceType.GIT_DIFF && !s.includedFiles().isEmpty()
                                    ? s.includedFiles().get(0).relativePath().split("\\|")[0] : null;
                            return new Preset.PresetSource(s.type().name(), s.path().toString(), mode);
                        })
                        .toList()
                : List.of();

        return new Preset(
                name,
                Objects.nonNull(agentConfig) ? agentConfig.filePath().toString() : "",
                presetSources,
                databaseConfig,
                Objects.nonNull(databaseConfig) ? databaseConfig.schema() : "",
                Objects.nonNull(mainController.getTableConfigs()) ? mainController.getTableConfigs() : List.of(),
                Objects.nonNull(mainController.getAdditionalContext()) ? mainController.getAdditionalContext() : "",
                mainController.getSettings(),
                outputFileNameField.getText().trim());
    }
}
