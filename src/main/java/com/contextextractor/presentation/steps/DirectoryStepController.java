package com.contextextractor.presentation.steps;

import com.contextextractor.domain.model.FileSource;
import com.contextextractor.domain.model.FileSourceConfig;
import com.contextextractor.domain.model.FileSourceType;
import com.contextextractor.domain.model.GitDiffMode;
import com.contextextractor.domain.model.ScannedFile;
import com.contextextractor.infrastructure.filesystem.RecursiveFileScanner;
import com.contextextractor.infrastructure.git.GitDiffRunner;
import com.contextextractor.presentation.components.ToastNotification;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class DirectoryStepController extends BaseStepController {

    @FXML private BorderPane rootPane;
    @FXML private Button addDirectoryButton;
    @FXML private Button addFilesButton;
    @FXML private Button addGitChangesButton;
    @FXML private VBox sourceListContainer;
    @FXML private Label summaryLabel;
    @FXML private Label emptyStateLabel;

    private final List<SourceEntry> entries = new ArrayList<>();
    private final AtomicInteger runningScanCount = new AtomicInteger(0);
    private final BooleanProperty anyScanning = new SimpleBooleanProperty(false);
    private final javafx.event.EventHandler<DragEvent> dragOverHandler = this::handleDragOver;
    private final javafx.event.EventHandler<DragEvent> dragDroppedHandler = this::handleDragDropped;
    private boolean initialized;

    @FXML
    private void initialize() {
        addDirectoryButton.disableProperty().bind(anyScanning);
        addFilesButton.disableProperty().bind(anyScanning);
        addGitChangesButton.disableProperty().bind(anyScanning);
        nextButton.disableProperty().bind(anyScanning);

        Tooltip gitTooltip = new Tooltip(
                "Includes a git diff in the generated context so the AI can see what changed.\n\n"
                + "Select the root folder of a git repository. You can choose which changes "
                + "to include (all, staged, unstaged, or full branch diff).");
        gitTooltip.setMaxWidth(320);
        gitTooltip.setWrapText(true);
        gitTooltip.setShowDelay(Duration.millis(200));
        Tooltip.install(addGitChangesButton, gitTooltip);

        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (Objects.nonNull(oldScene)) {
                oldScene.removeEventFilter(DragEvent.DRAG_OVER, dragOverHandler);
                oldScene.removeEventFilter(DragEvent.DRAG_DROPPED, dragDroppedHandler);
            }
            if (Objects.nonNull(newScene)) {
                newScene.addEventFilter(DragEvent.DRAG_OVER, dragOverHandler);
                newScene.addEventFilter(DragEvent.DRAG_DROPPED, dragDroppedHandler);
            }
        });
    }

    private void handleDragOver(DragEvent event) {
        Dragboard db = event.getDragboard();
        if (db.hasFiles() || db.hasString() || db.hasUrl() || !db.getContentTypes().isEmpty()) {
            event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
        }
        event.consume();
    }

    private void handleDragDropped(DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean success = false;

        if (db.hasFiles()) {
            for (File f : db.getFiles()) {
                if (f.isDirectory()) addDirectorySource(f.toPath());
                else addFileSource(f.toPath());
            }
            success = !db.getFiles().isEmpty();
        }

        if (!success) {
            success = resolveFromDragboard(db);
        }

        event.setDropCompleted(success);
        event.consume();
    }

    private boolean resolveFromDragboard(Dragboard db) {
        if (db.hasUrl()) {
            if (resolvePathsFromText(db.getUrl())) return true;
        }
        if (db.hasString()) {
            if (resolvePathsFromText(db.getString())) return true;
        }
        for (javafx.scene.input.DataFormat format : db.getContentTypes()) {
            Object content = db.getContent(format);
            if (content instanceof String text && !text.isBlank()) {
                if (resolvePathsFromText(text)) return true;
            }
        }
        return false;
    }

    @Override
    public void onNavigatedTo(int stepIndex) {
        super.onNavigatedTo(stepIndex);
        if (initialized) return;
        initialized = true;
        FileSourceConfig existing = mainController.getFileSourceConfig();
        if (Objects.nonNull(existing)) {
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
        if (Objects.nonNull(dir)) addDirectorySource(dir.toPath());
    }

    @FXML
    private void onAddFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Files");
        List<File> files = chooser.showOpenMultipleDialog(rootPane.getScene().getWindow());
        if (Objects.nonNull(files)) {
            for (File f : files) addFileSource(f.toPath());
        }
    }

    @FXML
    private void onAddGitChanges() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Git Repository");
        File dir = chooser.showDialog(rootPane.getScene().getWindow());
        if (Objects.isNull(dir)) return;
        Path path = dir.toPath().toAbsolutePath().normalize();
        if (!GitDiffRunner.isGitRepository(path)) {
            ToastNotification.showError(addGitChangesButton, "Not a git repository.");
            return;
        }
        if (entries.stream().anyMatch(e -> e.type == FileSourceType.GIT_DIFF && e.path.equals(path))) return;
        addGitDiffSource(path, GitDiffMode.ALL_CHANGES);
    }

    private boolean resolvePathsFromText(String raw) {
        if (Objects.isNull(raw) || raw.isBlank()) return false;
        boolean anyResolved = false;

        for (String line : raw.split("\\R")) {
            String candidate = line.trim();
            if (candidate.isBlank()) continue;

            if (candidate.startsWith("file:")) {
                candidate = resolveFileUri(candidate);
            }

            if (Objects.isNull(candidate)) continue;

            Path path = Path.of(candidate);
            if (Files.isDirectory(path)) {
                addDirectorySource(path);
                anyResolved = true;
            } else if (Files.isRegularFile(path)) {
                addFileSource(path);
                anyResolved = true;
            }
        }

        return anyResolved;
    }

    private String resolveFileUri(String uri) {
        try {
            String decoded = Path.of(new URI(uri).getPath()).toString();
            if (decoded.matches("/[A-Za-z]:.*")) {
                decoded = decoded.substring(1);
            }
            return decoded;
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
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

        runningScanCount.incrementAndGet();
        anyScanning.set(true);

        RecursiveFileScanner scanner = new RecursiveFileScanner(mainController.getSettings());
        Task<List<ScannedFile>> task = new Task<>() {
            @Override
            protected List<ScannedFile> call() {
                return scanner.scan(normalized);
            }
        };
        task.setOnSucceeded(e -> {
            if (runningScanCount.decrementAndGet() == 0) anyScanning.set(false);
            entry.applyFiles(task.getValue());
            updateSummary();
        });
        task.setOnFailed(e -> {
            if (runningScanCount.decrementAndGet() == 0) anyScanning.set(false);
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

        runningScanCount.incrementAndGet();
        anyScanning.set(true);

        Task<List<ScannedFile>> task = new Task<>() {
            @Override
            protected List<ScannedFile> call() {
                return new RecursiveFileScanner(mainController.getSettings()).scanSingle(normalized);
            }
        };
        task.setOnSucceeded(e -> {
            if (runningScanCount.decrementAndGet() == 0) anyScanning.set(false);
            List<ScannedFile> files = task.getValue();
            if (files.isEmpty()) return;
            SourceEntry entry = new SourceEntry(FileSourceType.FILE, normalized);
            entry.scannedFiles = new ArrayList<>(files);
            entries.add(entry);
            sourceListContainer.getChildren().add(entry.card);
            updateEmptyState();
            updateSummary();
        });
        task.setOnFailed(e -> {
            if (runningScanCount.decrementAndGet() == 0) anyScanning.set(false);
        });
        Thread t = new Thread(task, "file-scan");
        t.setDaemon(true);
        t.start();
    }

    private void addGitDiffSource(Path repoPath, GitDiffMode initialMode) {
        SourceEntry entry = new SourceEntry(FileSourceType.GIT_DIFF, repoPath, initialMode);
        entries.add(entry);
        sourceListContainer.getChildren().add(entry.card);
        updateEmptyState();
        runGitDiff(entry, initialMode);
    }

    private void runGitDiff(SourceEntry entry, GitDiffMode mode) {
        runningScanCount.incrementAndGet();
        anyScanning.set(true);
        int generation = entry.incrementDiffGeneration();

        Task<GitDiffRunner.DiffResult> task = new Task<>() {
            @Override
            protected GitDiffRunner.DiffResult call() throws Exception {
                return GitDiffRunner.runDiff(entry.path, mode);
            }
        };
        task.setOnSucceeded(e -> {
            if (runningScanCount.decrementAndGet() == 0) anyScanning.set(false);
            if (entry.isDiffGenerationCurrent(generation)) {
                GitDiffRunner.DiffResult result = task.getValue();
                String encoded = mode.name() + "|" + result.branch() + "|" + entry.path.toString();
                entry.commandLabel.setText(result.resolvedCommand());
                entry.applyGitDiff(List.of(new ScannedFile(encoded, result.output())));
                updateSummary();
            }
        });
        task.setOnFailed(e -> {
            if (runningScanCount.decrementAndGet() == 0) anyScanning.set(false);
            if (entry.isDiffGenerationCurrent(generation)) {
                entry.applyGitDiff(List.of());
                updateSummary();
            }
        });
        Thread t = new Thread(task, "git-diff");
        t.setDaemon(true);
        t.start();
    }

    private void restoreSource(FileSource source) {
        if (source.type() == FileSourceType.GIT_DIFF) {
            restoreGitDiffSource(source);
            return;
        }
        SourceEntry entry = new SourceEntry(source.type(), source.path());
        entry.scannedFiles = new ArrayList<>(source.includedFiles());
        entry.excludedPaths = new HashSet<>(source.excludedPaths());
        if (source.type() == FileSourceType.DIRECTORY) {
            entry.applyFilesInternal(source.includedFiles());
        }
        entries.add(entry);
        sourceListContainer.getChildren().add(entry.card);
    }

    private void restoreGitDiffSource(FileSource source) {
        if (source.includedFiles().isEmpty()) return;
        String[] parts = source.includedFiles().get(0).relativePath().split("\\|", 3);
        if (parts.length < 1) return;
        GitDiffMode mode;
        try { mode = GitDiffMode.valueOf(parts[0]); }
        catch (IllegalArgumentException ignored) { mode = GitDiffMode.ALL_CHANGES; }
        if (source.includedFiles().get(0).content().isBlank()) {
            addGitDiffSource(source.path(), mode);
        } else {
            SourceEntry entry = new SourceEntry(FileSourceType.GIT_DIFF, source.path(), mode);
            entry.applyGitDiff(new ArrayList<>(source.includedFiles()));
            entries.add(entry);
            sourceListContainer.getChildren().add(entry.card);
        }
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
        int totalFiles = config.allFiles().size();
        int diffSources = config.gitDiffSources().size();
        int srcs = entries.size();
        if (totalFiles == 0 && diffSources == 0) {
            summaryLabel.setText("No files selected");
        } else {
            StringBuilder sb = new StringBuilder();
            if (totalFiles > 0) {
                sb.append(totalFiles).append(totalFiles == 1 ? " file" : " files");
            }
            if (diffSources > 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(diffSources).append(diffSources == 1 ? " git diff" : " git diffs");
            }
            sb.append(" from ").append(srcs).append(srcs == 1 ? " source" : " sources");
            summaryLabel.setText(sb.toString());
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

    private class SourceEntry {

        final FileSourceType type;
        final Path path;
        List<ScannedFile> scannedFiles = new ArrayList<>();
        Set<Path> excludedPaths = new HashSet<>();
        final VBox card;

        private volatile int diffGeneration = 0;

        private Label expandIcon;
        private Label badgeLabel;
        private Label commandLabel;
        private ProgressIndicator spinner;
        private VBox fileListBox;
        private boolean expanded = false;

        SourceEntry(FileSourceType type, Path path) {
            this.type = type;
            this.path = path;
            this.card = type == FileSourceType.DIRECTORY ? buildDirectoryCard() : buildFileCard();
        }

        SourceEntry(FileSourceType type, Path path, GitDiffMode initialMode) {
            this.type = type;
            this.path = path;
            this.card = buildGitDiffCard(initialMode);
        }

        int incrementDiffGeneration() { return ++diffGeneration; }
        boolean isDiffGenerationCurrent(int gen) { return diffGeneration == gen; }

        private VBox buildDirectoryCard() {
            expandIcon = new Label("▶");
            expandIcon.getStyleClass().add("db-expand-icon");
            spinner = new ProgressIndicator();
            spinner.getStyleClass().add("scanning-indicator");
            spinner.setPrefSize(16, 16);
            badgeLabel = new Label();
            badgeLabel.getStyleClass().add("files-skipped-label");
            badgeLabel.setVisible(false);
            badgeLabel.setManaged(false);

            HBox header = buildScanningHeader();

            fileListBox = new VBox(2);
            fileListBox.setPadding(new Insets(8, 12, 12, 16));
            fileListBox.getStyleClass().add("db-table-detail");
            fileListBox.setVisible(false);
            fileListBox.setManaged(false);

            VBox card = new VBox(header, fileListBox);
            card.getStyleClass().add("db-table-row");
            return card;
        }

        private HBox buildScanningHeader() {
            Label icon = new Label("📁");
            Label pathLabel = new Label(path.toString());
            pathLabel.getStyleClass().add("db-table-name");

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
            return header;
        }

        private VBox buildFileCard() {
            Label icon = new Label("📄");
            Label nameLabel = new Label(path.getFileName().toString());
            nameLabel.getStyleClass().add("db-table-name");

            Label parentLabel = new Label(Objects.nonNull(path.getParent()) ? "  " + path.getParent() : "");
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

        private VBox buildGitDiffCard(GitDiffMode initialMode) {
            spinner = new ProgressIndicator();
            spinner.getStyleClass().add("scanning-indicator");
            spinner.setPrefSize(16, 16);

            badgeLabel = new Label();
            badgeLabel.getStyleClass().add("files-skipped-label");
            badgeLabel.setVisible(false);
            badgeLabel.setManaged(false);

            Label icon = new Label("🔀");
            String repoName = Objects.nonNull(path.getFileName()) ? path.getFileName().toString() : path.toString();
            Label nameLabel = new Label(repoName);
            nameLabel.getStyleClass().add("db-table-name");

            Label pathLabel = new Label("  " + path);
            pathLabel.getStyleClass().add("step-description");

            ChoiceBox<GitDiffMode> modeChoiceBox = new ChoiceBox<>();
            modeChoiceBox.getItems().addAll(GitDiffMode.values());
            modeChoiceBox.setValue(initialMode);
            modeChoiceBox.setOnAction(e -> onModeChanged(modeChoiceBox.getValue()));

            Button refreshBtn = new Button("\u21BB");
            refreshBtn.getStyleClass().add("browse-button");
            refreshBtn.setPadding(new Insets(4, 8, 4, 8));
            refreshBtn.setOnAction(e -> runGitDiff(this, modeChoiceBox.getValue()));

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button removeBtn = new Button("×");
            removeBtn.getStyleClass().add("tag-chip-remove");
            removeBtn.setOnAction(e -> removeEntry(this));

            HBox header = new HBox(8, icon, nameLabel, pathLabel, spacer, modeChoiceBox, refreshBtn, spinner, badgeLabel, removeBtn);
            header.setAlignment(Pos.CENTER_LEFT);
            header.getStyleClass().add("db-table-header");
            header.setPadding(new Insets(10, 12, 10, 12));

            commandLabel = new Label(initialMode.commandHint());
            commandLabel.getStyleClass().add("step-description");
            commandLabel.setPadding(new Insets(0, 12, 8, 42));

            VBox card = new VBox(header, commandLabel);
            card.getStyleClass().add("db-table-row");
            return card;
        }

        private void onModeChanged(GitDiffMode newMode) {
            scannedFiles = new ArrayList<>();
            badgeLabel.setVisible(false);
            badgeLabel.setManaged(false);
            commandLabel.setText(newMode.commandHint());
            runGitDiff(this, newMode);
        }

        void applyFiles(List<ScannedFile> files) {
            scannedFiles = new ArrayList<>(files);
            spinner.setVisible(false);
            spinner.setManaged(false);
            badgeLabel.setText("(" + files.size() + (files.size() == 1 ? " file)" : " files)"));
            badgeLabel.setVisible(true);
            badgeLabel.setManaged(true);
            if (expanded) rebuildFileList();
        }

        void applyFilesInternal(List<ScannedFile> files) {
            scannedFiles = new ArrayList<>(files);
            if (Objects.nonNull(spinner)) { spinner.setVisible(false); spinner.setManaged(false); }
            if (Objects.nonNull(badgeLabel)) {
                badgeLabel.setText("(" + files.size() + (files.size() == 1 ? " file)" : " files)"));
                badgeLabel.setVisible(true);
                badgeLabel.setManaged(true);
            }
        }

        void applyGitDiff(List<ScannedFile> files) {
            scannedFiles = new ArrayList<>(files);
            spinner.setVisible(false);
            spinner.setManaged(false);
            if (files.isEmpty()) {
                badgeLabel.setText("(no changes)");
            } else {
                long lines = files.get(0).content().lines().count();
                badgeLabel.setText("(" + lines + (lines == 1 ? " line)" : " lines)"));
            }
            badgeLabel.setVisible(true);
            badgeLabel.setManaged(true);
        }

        private void rebuildFileList() {
            if (Objects.isNull(fileListBox)) return;
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
            if (expanded) rebuildFileList();
            fileListBox.setVisible(expanded);
            fileListBox.setManaged(expanded);
        }

        FileSource toFileSource() {
            return new FileSource(type, path, List.copyOf(scannedFiles), Set.copyOf(excludedPaths));
        }
    }
}
