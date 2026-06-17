package com.contextextractor.presentation.settings;

import atlantafx.base.controls.ToggleSwitch;
import com.contextextractor.domain.model.AppSettings;
import com.contextextractor.presentation.MainController;
import com.contextextractor.presentation.ThemeManager;
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
import java.util.Objects;

public class SettingsController {

    @FXML private ToggleSwitch darkModeToggle;
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
        darkModeToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (Objects.nonNull(darkModeToggle.getScene())) {
                ThemeManager.apply(darkModeToggle.getScene(), newVal);
                AppSettings current = mainController.getSettings();
                AppSettings updated = new AppSettings(
                        current.maxXmlSizeMb(),
                        current.excludedPatterns(),
                        current.outputDirectory(),
                        current.presetsDirectory(),
                        newVal);
                mainController.saveSettings(updated);
            }
        });
    }

    public void onNavigatedToSettings() {
        AppSettings current = mainController.getSettings();
        darkModeToggle.setSelected(current.darkMode());
        maxSizeSpinner.getValueFactory().setValue(current.maxXmlSizeMb());
        tagListEditor.setValues(current.excludedPatterns());
        outputDirField.setText(Objects.nonNull(current.outputDirectory()) ? current.outputDirectory() : "");
        presetsDirField.setText(Objects.nonNull(current.presetsDirectory()) ? current.presetsDirectory() : "");
    }

    @FXML
    private void onSave() {
        AppSettings updated = new AppSettings(
                maxSizeSpinner.getValue(),
                tagListEditor.getValues(),
                outputDirField.getText().trim(),
                presetsDirField.getText().trim(),
                darkModeToggle.isSelected());
        mainController.saveSettings(updated);
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
                darkModeToggle.setSelected(defaults.darkMode());
                maxSizeSpinner.getValueFactory().setValue(defaults.maxXmlSizeMb());
                tagListEditor.setValues(defaults.excludedPatterns());
                outputDirField.setText(Objects.nonNull(defaults.outputDirectory()) ? defaults.outputDirectory() : "");
                presetsDirField.setText(Objects.nonNull(defaults.presetsDirectory()) ? defaults.presetsDirectory() : "");
                mainController.saveSettings(defaults);
                ToastNotification.showSuccess(maxSizeSpinner, "Reset to defaults \u2713");
            }
        });
    }

    @FXML
    private void onBrowseOutputDir() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Output Directory");
        File selected = chooser.showDialog(outputDirField.getScene().getWindow());
        if (Objects.nonNull(selected)) outputDirField.setText(selected.getAbsolutePath());
    }

    @FXML
    private void onBrowsePresetsDir() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Presets Directory");
        File selected = chooser.showDialog(presetsDirField.getScene().getWindow());
        if (Objects.nonNull(selected)) presetsDirField.setText(selected.getAbsolutePath());
    }

    @FXML
    private void onBack() {
        ThemeManager.apply(darkModeToggle.getScene(), mainController.getSettings().darkMode());
        mainController.navigateTo(mainController.getCurrentStep());
    }
}
