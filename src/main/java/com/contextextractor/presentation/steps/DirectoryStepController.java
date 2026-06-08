package com.contextextractor.presentation.steps;

import com.contextextractor.domain.model.FileSource;
import com.contextextractor.domain.model.FileSourceConfig;
import com.contextextractor.domain.model.FileSourceType;
import com.contextextractor.domain.model.ScannedFile;
import com.contextextractor.infrastructure.filesystem.RecursiveFileScanner;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DirectoryStepController extends BaseStepController {

    @FXML private BorderPane rootPane;
    @FXML private VBox sourceListContainer;
    @FXML private Label summaryLabel;
    @FXML private Label emptyStateLabel;

    private final List<SourceEntry> entries = new ArrayList<>();

    @FXML
    private void initialize() {
        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.removeEventFilter(DragEvent.DRAG_OVER, this::handleDragOver);
                oldScene.removeEventFilter(DragEvent.DRAG_DROPPED, this::handleDragDropped);
            }
            if (newScene != null) {
                newScene.addEventFilter(DragEvent.DRAG_OVER, this::handleDragOver);
                newScene.addEventFilter(DragEvent.DRAG_DROPPED, this::handleDragDropped);
            }
        });
    }

    private void handleDragOver(DragEvent event) {
        if (event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    private void handleDragDropped(DragEvent event) {
        for (File f : event.getDragboard().getFiles()) {
            if (f.isDirectory()) addDirectorySource(f.toPath());
            else addFileSource(f.toPath());
        }
        event.setDropCompleted(true);
        event.consume();
    }

    @Override
    public void onNavigatedTo(int stepIndex) {
        super.onNavigatedTo(stepIndex);
        FileSourceConfig existing = mainController.getFileSourceConfig();
        if (existing != null) {
            for (FileSource source : existing.sources()) {
                restoreSource(source);
            }
        }
        updateEmptyState();
        updateSummary();
    }

    @FXML
    private void onAddDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Directory");
        File dir = chooser.showDialog(rootPane.getScene().getWindow());
        if (dir != null) addDirectorySource(dir.toPath());
    }

    @FXML
    private void onAddFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Files");
        List<File> files = chooser.showOpenMultipleDialog(rootPane.getScene().getWindow());
        if (files != null) {
            for (File f : files) addFileSource(f.toPath());
        }
    }

    private void addDirectorySource(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (entries.stream().anyMatch(e -> e.path.equals(normalized))) return;

        SourceEntry entry = new SourceEntry(FileSourceType.DIRECTORY, normalized);
        entries.add(entry);
        sourceListContainer.getChildren().add(entry.card);
        updateEmptyState();
        updateSummary();

        RecursiveFileScanner scanner = new RecursiveFileScanner(mainController.getSettings());
        Task<List<ScannedFile>> task = new Task<>() {
            @Override
            protected List<ScannedFile> call() {
                return scanner.scan(normalized);
            }
        };
        task.setOnSucceeded(e -> {
            entry.applyFiles(task.getValue());
            updateSummary();
        });
        task.setOnFailed(e -> {
            entry.applyFiles(List.of());
            updateSummary();
        });
        Thread t = new Thread(task, "dir-scan");
        t.setDaemon(true);
        t.start();
    }

    private void addFileSource(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (entries.stream().anyMatch(e -> e.path.equals(normalized))) return;

        List<ScannedFile> files = new RecursiveFileScanner(mainController.getSettings()).scanSingle(normalized);
        if (files.isEmpty()) return;

        SourceEntry entry = new SourceEntry(FileSourceType.FILE, normalized);
        entry.scannedFiles = new ArrayList<>(files);
        entries.add(entry);
        sourceListContainer.getChildren().add(entry.card);
        updateEmptyState();
        updateSummary();
    }

    private void restoreSource(FileSource source) {
        SourceEntry entry = new SourceEntry(source.type(), source.path());
        entry.scannedFiles = new ArrayList<>(source.includedFiles());
        entry.excludedPaths = new HashSet<>(source.excludedPaths());
        if (source.type() == FileSourceType.DIRECTORY) {
            entry.applyFilesInternal(source.includedFiles());
        }
        entries.add(entry);
        sourceListContainer.getChildren().add(entry.card);
    }

    private void removeEntry(SourceEntry entry) {
        entries.remove(entry);
        sourceListContainer.getChildren().remove(entry.card);
        updateEmptyState();
        updateSummary();
    }

    private void updateEmptyState() {
        boolean empty = entries.isEmpty();
        emptyStateLabel.setVisible(empty);
        emptyStateLabel.setManaged(empty);
    }

    private void updateSummary() {
        FileSourceConfig config = buildFileSourceConfig();
        mainController.setFileSourceConfig(config);
        int total = config.allFiles().size();
        int srcs = entries.size();
        if (total == 0) {
            summaryLabel.setText("No files selected");
        } else {
            summaryLabel.setText(total + (total == 1 ? " file" : " files")
                    + " from " + srcs + (srcs == 1 ? " source" : " sources"));
        }
    }

    private FileSourceConfig buildFileSourceConfig() {
        return new FileSourceConfig(entries.stream().map(SourceEntry::toFileSource).toList());
    }

    @Override
    protected void onNext() {
        mainController.setFileSourceConfig(buildFileSourceConfig());
        mainController.nextStep();
    }

    private static void runOnFx(Runnable r) {
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }

    private class SourceEntry {

        final FileSourceType type;
        final Path path;
        List<ScannedFile> scannedFiles = new ArrayList<>();
        Set<Path> excludedPaths = new HashSet<>();
        final VBox card;

        private Label expandIcon;
        private Label badgeLabel;
        private ProgressIndicator spinner;
        private VBox fileListBox;
        private boolean expanded = false;

        SourceEntry(FileSourceType type, Path path) {
            this.type = type;
            this.path = path;
            this.card = type == FileSourceType.DIRECTORY ? buildDirectoryCard() : buildFileCard();
        }

        private VBox buildDirectoryCard() {
            expandIcon = new Label("▶");
            expandIcon.getStyleClass().add("db-expand-icon");

            Label icon = new Label("📁");
            Label pathLabel = new Label(path.toString());
            pathLabel.getStyleClass().add("db-table-name");

            spinner = new ProgressIndicator();
            spinner.setPrefSize(16, 16);

            badgeLabel = new Label();
            badgeLabel.getStyleClass().add("files-skipped-label");
            badgeLabel.setVisible(false);
            badgeLabel.setManaged(false);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button removeBtn = new Button("×");
            removeBtn.getStyleClass().add("tag-chip-remove");
            removeBtn.setOnAction(e -> removeEntry(this));

            HBox header = new HBox(8, expandIcon, icon, pathLabel, spacer, spinner, badgeLabel, removeBtn);
            header.setAlignment(Pos.CENTER_LEFT);
            header.getStyleClass().add("db-table-header");
            header.setPadding(new Insets(10, 12, 10, 12));
            header.setCursor(Cursor.HAND);
            header.setOnMouseClicked(e -> toggleExpand());

            fileListBox = new VBox(2);
            fileListBox.setPadding(new Insets(8, 12, 12, 16));
            fileListBox.getStyleClass().add("db-table-detail");
            fileListBox.setVisible(false);
            fileListBox.setManaged(false);

            VBox card = new VBox(header, fileListBox);
            card.getStyleClass().add("db-table-row");
            return card;
        }

        private VBox buildFileCard() {
            Label icon = new Label("📄");
            Label nameLabel = new Label(path.getFileName().toString());
            nameLabel.getStyleClass().add("db-table-name");

            Label parentLabel = new Label(path.getParent() != null ? "  " + path.getParent() : "");
            parentLabel.getStyleClass().add("step-description");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button removeBtn = new Button("×");
            removeBtn.getStyleClass().add("tag-chip-remove");
            removeBtn.setOnAction(e -> removeEntry(this));

            HBox row = new HBox(8, icon, nameLabel, parentLabel, spacer, removeBtn);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(10, 12, 10, 12));
            row.getStyleClass().add("db-table-header");

            VBox card = new VBox(row);
            card.getStyleClass().add("db-table-row");
            return card;
        }

        void applyFiles(List<ScannedFile> files) {
            runOnFx(() -> {
                scannedFiles = new ArrayList<>(files);
                spinner.setVisible(false);
                spinner.setManaged(false);
                badgeLabel.setText("(" + files.size() + (files.size() == 1 ? " file)" : " files)"));
                badgeLabel.setVisible(true);
                badgeLabel.setManaged(true);
                rebuildFileList();
            });
        }

        void applyFilesInternal(List<ScannedFile> files) {
            scannedFiles = new ArrayList<>(files);
            if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
            if (badgeLabel != null) {
                badgeLabel.setText("(" + files.size() + (files.size() == 1 ? " file)" : " files)"));
                badgeLabel.setVisible(true);
                badgeLabel.setManaged(true);
            }
            rebuildFileList();
        }

        private void rebuildFileList() {
            if (fileListBox == null) return;
            fileListBox.getChildren().clear();
            if (scannedFiles.isEmpty()) return;

            for (ScannedFile file : scannedFiles) {
                Path abs = path.resolve(file.relativePath()).normalize();
                if (excludedPaths.contains(abs)) continue;

                Label fileLabel = new Label("📄  " + file.relativePath());
                fileLabel.getStyleClass().add("step-description");

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                Button removeBtn = new Button("×");
                removeBtn.getStyleClass().add("tag-chip-remove");

                HBox row = new HBox(6, fileLabel, spacer, removeBtn);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(2, 4, 2, 4));

                removeBtn.setOnAction(e -> {
                    excludedPaths.add(abs);
                    fileListBox.getChildren().remove(row);
                    updateBadge();
                    updateSummary();
                });

                fileListBox.getChildren().add(row);
            }
        }

        private void updateBadge() {
            long visible = scannedFiles.stream()
                    .filter(f -> !excludedPaths.contains(path.resolve(f.relativePath()).normalize()))
                    .count();
            badgeLabel.setText("(" + visible + (visible == 1 ? " file)" : " files)"));
        }

        private void toggleExpand() {
            if (scannedFiles.isEmpty()) return;
            expanded = !expanded;
            expandIcon.setText(expanded ? "▼" : "▶");
            fileListBox.setVisible(expanded);
            fileListBox.setManaged(expanded);
        }

        FileSource toFileSource() {
            return new FileSource(type, path, List.copyOf(scannedFiles), Set.copyOf(excludedPaths));
        }
    }
}
