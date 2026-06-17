package com.contextextractor.presentation.steps;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

import java.util.Objects;

public class AdditionalContextStepController extends BaseStepController {

    @FXML private TextArea contextArea;
    @FXML private Label charCountLabel;

    private boolean initialized;

    @Override
    public void onNavigatedTo(int stepIndex) {
        super.onNavigatedTo(stepIndex);
        if (initialized) return;
        initialized = true;
        String existing = mainController.getAdditionalContext();
        if (Objects.nonNull(existing)) {
            contextArea.setText(existing);
        }
        updateCharCount();
        contextArea.textProperty().addListener((obs, old, val) -> {
            updateCharCount();
            mainController.setAdditionalContext(val);
        });
    }

    private void updateCharCount() {
        int count = contextArea.getText().length();
        charCountLabel.setText(String.format("%,d characters", count));
    }
}
