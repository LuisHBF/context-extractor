package com.contextextractor.presentation.steps;

import com.contextextractor.domain.model.DirectoryConfig;
import com.contextextractor.domain.model.ScannedFile;
import com.contextextractor.infrastructure.filesystem.RecursiveFileScanner;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class DirectoryStepController extends BaseStepController {

    @FXML private TextField directoryPathField;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private VBox summaryBox;
    @FXML private Label filesFoundLabel;
    @FXML private Label filesSkippedLabel;
    @FXML private Label noFilesLabel;
    @FXML private ListView<String> fileListView;

    private Path selectedDirectory;
    private DirectoryConfig directoryConfig;
    private Task<List<ScannedFile>> currentScanTask;

    @Override
    public void onNavigatedTo(int stepIndex) {
        super.onNavigatedTo(stepIndex);
        DirectoryConfig existing = mainController.getDirectoryConfig();
        if (existing != null) {
            selectedDirectory = existing.rootPath();
            directoryPathField.setText(existing.rootPath().toString());
            filesFoundLabel.setText(existing.files().size() + " files found");
            filesSkippedLabel.setText("");
            fileListView.getItems().setAll(
                    existing.files().stream().map(ScannedFile::relativePath).toList());
            summaryBox.setVisible(true);
            summaryBox.setManaged(true);
            directoryConfig = existing;
        }
    }

    @FXML
    private void onBrowse() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Project Directory");

        File selected = chooser.showDialog(directoryPathField.getScene().getWindow());
        if (selected == null) return;

        selectedDirectory = selected.toPath();
        directoryPathField.setText(selected.getAbsolutePath());
        startScan();
    }

    private void startScan() {
        if (currentScanTask != null && currentScanTask.isRunning()) {
            currentScanTask.cancel();
        }
        if (progressIndicator.visibleProperty().isBound()) {
            progressIndicator.visibleProperty().unbind();
            progressIndicator.managedProperty().unbind();
        }

        summaryBox.setVisible(false);
        summaryBox.setManaged(false);
        fileListView.getItems().clear();
        directoryConfig = null;

        RecursiveFileScanner scanner = new RecursiveFileScanner(mainController.getSettings());

        Task<List<ScannedFile>> task = new Task<>() {
            @Override
            protected List<ScannedFile> call() {
                return scanner.scan(selectedDirectory);
            }
        };

        progressIndicator.visibleProperty().bind(task.runningProperty());
        progressIndicator.managedProperty().bind(task.runningProperty());

        task.setOnSucceeded(e -> showResults(task.getValue(), scanner.getLastSkippedCount()));
        task.setOnFailed(e -> showError());

        currentScanTask = task;
        Thread thread = new Thread(task, "file-scanner");
        thread.setDaemon(true);
        thread.start();
    }

    private void showResults(List<ScannedFile> files, int skipped) {
        summaryBox.setVisible(true);
        summaryBox.setManaged(true);
        filesFoundLabel.setText(files.size() + " files found");
        filesSkippedLabel.setText(skipped + " files skipped");

        if (files.isEmpty()) {
            noFilesLabel.setVisible(true);
            noFilesLabel.setManaged(true);
        } else {
            noFilesLabel.setVisible(false);
            noFilesLabel.setManaged(false);
            fileListView.getItems().setAll(
                    files.stream().map(ScannedFile::relativePath).toList());
            directoryConfig = new DirectoryConfig(selectedDirectory, files);
        }
    }

    private void showError() {
        summaryBox.setVisible(true);
        summaryBox.setManaged(true);
        filesFoundLabel.setText("0 files found");
        filesSkippedLabel.setText("");
        noFilesLabel.setText("Error scanning directory.");
        noFilesLabel.setVisible(true);
        noFilesLabel.setManaged(true);
    }

    @Override
    protected void onNext() {
        mainController.setDirectoryConfig(directoryConfig);
        mainController.nextStep();
    }
}
