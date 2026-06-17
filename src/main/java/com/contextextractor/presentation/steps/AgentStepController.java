package com.contextextractor.presentation.steps;

import com.contextextractor.domain.model.AgentConfig;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class AgentStepController extends BaseStepController {

    @FXML private TextField filePathField;
    @FXML private TextArea previewArea;
    @FXML private VBox dropZone;

    private AgentConfig agentConfig;
    private boolean initialized;

    @Override
    public void onNavigatedTo(int stepIndex) {
        super.onNavigatedTo(stepIndex);
        if (initialized) return;
        initialized = true;
        AgentConfig existing = mainController.getAgentConfig();
        if (Objects.nonNull(existing)) {
            agentConfig = existing;
            filePathField.setText(existing.filePath().toString());
            previewArea.setText(existing.content());
        }
    }

    @FXML
    private void onBrowse() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Agent File");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Agent Files (*.md, *.agent)", "*.md", "*.agent"));

        File selected = chooser.showOpenDialog(filePathField.getScene().getWindow());
        if (Objects.isNull(selected)) return;
        loadFile(selected.toPath());
    }

    @FXML
    private void onDragOver(DragEvent event) {
        if (isValidAgentFileDrop(event)) {
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    @FXML
    private void onDragEntered(DragEvent event) {
        if (isValidAgentFileDrop(event)) {
            dropZone.getStyleClass().add("agent-drop-zone-active");
        }
        event.consume();
    }

    @FXML
    private void onDragExited(DragEvent event) {
        dropZone.getStyleClass().remove("agent-drop-zone-active");
        event.consume();
    }

    @FXML
    private void onDragDropped(DragEvent event) {
        dropZone.getStyleClass().remove("agent-drop-zone-active");
        List<File> files = event.getDragboard().getFiles();
        if (!files.isEmpty()) {
            loadFile(files.get(0).toPath());
            event.setDropCompleted(true);
        } else {
            event.setDropCompleted(false);
        }
        event.consume();
    }

    private boolean isValidAgentFileDrop(DragEvent event) {
        if (!event.getDragboard().hasFiles()) return false;
        List<File> files = event.getDragboard().getFiles();
        if (files.size() != 1) return false;
        String name = files.get(0).getName().toLowerCase();
        return name.endsWith(".md") || name.endsWith(".agent");
    }

    private void loadFile(Path path) {
        try {
            String content = Files.readString(path);
            agentConfig = new AgentConfig(path, content);
            filePathField.setText(path.toAbsolutePath().toString());
            previewArea.setText(content);
            mainController.setAgentConfig(agentConfig);
            mainController.markStepCompleted(0);
        } catch (IOException e) {
            previewArea.setText("Unable to read file.");
            agentConfig = null;
            mainController.setAgentConfig(null);
        }
    }
}
