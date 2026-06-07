package com.contextextractor.presentation.steps;

import com.contextextractor.domain.model.AgentConfig;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class AgentStepController extends BaseStepController {

    @FXML private TextField filePathField;
    @FXML private TextArea previewArea;

    private AgentConfig agentConfig;

    @Override
    public void onNavigatedTo(int stepIndex) {
        super.onNavigatedTo(stepIndex);
        AgentConfig existing = mainController.getAgentConfig();
        if (existing != null) {
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
        if (selected == null) return;

        try {
            String content = Files.readString(selected.toPath());
            agentConfig = new AgentConfig(selected.toPath(), content);
            filePathField.setText(selected.getAbsolutePath());
            previewArea.setText(content);
            mainController.markStepCompleted(0);
        } catch (IOException e) {
            previewArea.setText("Unable to read file.");
            agentConfig = null;
        }
    }

    @Override
    protected void onNext() {
        mainController.setAgentConfig(agentConfig);
        mainController.nextStep();
    }


}
