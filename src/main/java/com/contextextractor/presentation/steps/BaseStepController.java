package com.contextextractor.presentation.steps;

import com.contextextractor.presentation.MainController;
import javafx.fxml.FXML;
import javafx.scene.control.Button;

public abstract class BaseStepController {

    @FXML protected Button backButton;
    @FXML protected Button nextButton;

    protected MainController mainController;

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    public void onNavigatedTo(int stepIndex) {
        backButton.setVisible(stepIndex > 0);
        backButton.setManaged(stepIndex > 0);
        nextButton.setVisible(stepIndex < 4);
        nextButton.setManaged(stepIndex < 4);
    }

    @FXML
    protected void onNext() {
        mainController.nextStep();
    }

    @FXML
    protected void onBack() {
        mainController.previousStep();
    }
}
