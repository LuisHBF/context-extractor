package com.contextextractor.presentation.settings;

import com.contextextractor.domain.model.AppSettings;
import com.contextextractor.infrastructure.persistence.SettingsRepository;
import com.contextextractor.presentation.MainController;
import com.contextextractor.presentation.components.TagListEditor;
import com.contextextractor.presentation.components.ToastNotification;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;

import java.io.File;

public class SettingsController {

    @FXML private Spinner<Integer> maxSizeSpinner;
    @FXML private TagListEditor tagListEditor;
    @FXML private TextField outputDirField;
    @FXML private TextField presetsDirField;

    private MainController mainController;

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    @FXML
    private void initialize() {
        maxSizeSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 6));
        maxSizeSpinner.setEditable(true);
    }

    public void onNavigatedToSettings() {
        AppSettings current = mainController.getSettings();
        maxSizeSpinner.getValueFactory().setValue(current.maxXmlSizeMb());
        tagListEditor.setValues(current.excludedPatterns());
        outputDirField.setText(current.outputDirectory() != null ? current.outputDirectory() : "");
        presetsDirField.setText(current.presetsDirectory() != null ? current.presetsDirectory() : "");
    }

    @FXML
    private void onSave() {
        AppSettings updated = new AppSettings(
                maxSizeSpinner.getValue(),
                tagListEditor.getValues(),
                outputDirField.getText().trim(),
                presetsDirField.getText().trim());
        new SettingsRepository().save(updated);
        mainController.updateSettings(updated);
        ToastNotification.showSuccess(maxSizeSpinner, "Settings saved \u2713");
    }

    @FXML
    private void onResetToDefaults() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Reset all settings to defaults?", ButtonType.YES, ButtonType.NO);
        alert.setTitle("Reset Settings");
        alert.setHeaderText(null);
        alert.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                AppSettings defaults = AppSettings.defaults();
                maxSizeSpinner.getValueFactory().setValue(defaults.maxXmlSizeMb());
                tagListEditor.setValues(defaults.excludedPatterns());
                outputDirField.setText(defaults.outputDirectory() != null ? defaults.outputDirectory() : "");
                presetsDirField.setText(defaults.presetsDirectory() != null ? defaults.presetsDirectory() : "");
                new SettingsRepository().save(defaults);
                mainController.updateSettings(defaults);
                ToastNotification.showSuccess(maxSizeSpinner, "Reset to defaults \u2713");
            }
        });
    }

    @FXML
    private void onBrowseOutputDir() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Output Directory");
        File selected = chooser.showDialog(outputDirField.getScene().getWindow());
        if (selected != null) outputDirField.setText(selected.getAbsolutePath());
    }

    @FXML
    private void onBrowsePresetsDir() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Presets Directory");
        File selected = chooser.showDialog(presetsDirField.getScene().getWindow());
        if (selected != null) presetsDirField.setText(selected.getAbsolutePath());
    }

    @FXML
    private void onBack() {
        mainController.navigateTo(mainController.getCurrentStep());
    }
}
