package com.contextextractor.presentation.steps;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

public class AdditionalContextStepController extends BaseStepController {

    @FXML private TextArea contextArea;
    @FXML private Label charCountLabel;

    @Override
    public void onNavigatedTo(int stepIndex) {
        super.onNavigatedTo(stepIndex);
        String existing = mainController.getAdditionalContext();
        if (existing != null) {
            contextArea.setText(existing);
        }
        updateCharCount();
        contextArea.textProperty().addListener((obs, old, val) -> updateCharCount());
    }

    @Override
    protected void onNext() {
        mainController.setAdditionalContext(contextArea.getText());
        mainController.nextStep();
    }

    private void updateCharCount() {
        int count = contextArea.getText().length();
        charCountLabel.setText(String.format("%,d characters", count));
    }
}
