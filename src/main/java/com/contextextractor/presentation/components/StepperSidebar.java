package com.contextextractor.presentation.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class StepperSidebar extends VBox {

    private static final String[] STEP_NAMES = {
            "Agent", "Directory", "Database", "Additional Context", "Review & Generate"
    };

    private static final double CIRCLE_RADIUS = 14;
    private static final double ROW_LEFT_PADDING = 16;
    private static final double INDICATOR_SIZE = 32;

    private int activeStep = -1;
    private final boolean[] completed = new boolean[5];
    private final List<HBox> rows = new ArrayList<>();
    private final List<Circle> circles = new ArrayList<>();
    private final List<Label> numberLabels = new ArrayList<>();
    private final List<Label> stepLabels = new ArrayList<>();

    private Consumer<Integer> onStepClicked;
    private Runnable onSettingsClicked;

    public StepperSidebar() {
        getStyleClass().add("stepper-sidebar");
        buildUI();
    }

    private void buildUI() {
        VBox stepsBox = new VBox();
        stepsBox.setPadding(new Insets(24, 0, 8, 0));
        VBox.setVgrow(stepsBox, Priority.ALWAYS);

        for (int i = 0; i < 5; i++) {
            stepsBox.getChildren().add(buildRow(i));
            if (i < 4) {
                stepsBox.getChildren().add(buildConnector());
            }
        }

        Button settingsBtn = new Button("⚙  Settings");
        settingsBtn.getStyleClass().add("sidebar-settings-button");
        settingsBtn.setOnAction(e -> { if (onSettingsClicked != null) onSettingsClicked.run(); });
        VBox.setMargin(settingsBtn, new Insets(0, 0, 8, 12));

        Label aboutLabel = new Label("ⓘ  v1.1.0");
        aboutLabel.getStyleClass().add("sidebar-about-label");
        aboutLabel.setCursor(Cursor.HAND);
        aboutLabel.setOnMouseClicked(e -> showAboutDialog());
        VBox.setMargin(aboutLabel, new Insets(0, 0, 16, 16));

        getChildren().addAll(stepsBox, settingsBtn, aboutLabel);
    }

    private HBox buildRow(int index) {
        Circle circle = new Circle(CIRCLE_RADIUS);
        circles.add(circle);

        Label number = new Label(String.valueOf(index + 1));
        number.getStyleClass().add("step-number");
        numberLabels.add(number);

        StackPane indicator = new StackPane(circle, number);
        indicator.setPrefSize(INDICATOR_SIZE, INDICATOR_SIZE);
        indicator.setMinSize(INDICATOR_SIZE, INDICATOR_SIZE);
        indicator.setMaxSize(INDICATOR_SIZE, INDICATOR_SIZE);

        Label stepLabel = new Label(STEP_NAMES[index]);
        stepLabel.getStyleClass().add("step-label");
        stepLabels.add(stepLabel);

        Label optionalBadge = new Label("optional");
        optionalBadge.getStyleClass().add("step-optional-badge");

        VBox labelBox = new VBox(2, stepLabel, optionalBadge);
        HBox row = new HBox(12, indicator, labelBox);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(5, 16, 5, ROW_LEFT_PADDING));
        rows.add(row);

        final int idx = index;
        row.setOnMouseClicked(e -> {
            if (onStepClicked != null) onStepClicked.accept(idx);
        });

        return row;
    }

    private Region buildConnector() {
        Region line = new Region();
        line.getStyleClass().add("step-connector");
        line.setPrefHeight(16);
        line.setPrefWidth(2);
        line.setMaxWidth(2);
        VBox.setMargin(line, new Insets(0, 0, 0, ROW_LEFT_PADDING + INDICATOR_SIZE / 2 - 1));
        return line;
    }

    public void setActiveStep(int stepIndex) {
        activeStep = stepIndex;
        refresh();
    }

    public void markCompleted(int stepIndex) {
        completed[stepIndex] = true;
        refresh();
    }

    private void showAboutDialog() {
        Label title = new Label("Context Extractor  v1.1.0");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1E1E2E;");

        Label author = new Label("Developed by Luis Franco");
        author.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");

        Hyperlink link = new Hyperlink("github.com/LuisHBF/content-extractor");
        link.setStyle("-fx-font-size: 12px;");
        link.setOnAction(e -> {
            try {
                Desktop.getDesktop().browse(new URI("https://github.com/LuisHBF/content-extractor"));
            } catch (Exception ignored) {}
        });

        VBox content = new VBox(6, title, author, link);
        content.setPadding(new Insets(8, 4, 4, 4));

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("About");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    public void setOnStepClicked(Consumer<Integer> handler) {
        onStepClicked = handler;
    }

    public void setOnSettingsClicked(Runnable handler) {
        onSettingsClicked = handler;
    }

    private void refresh() {
        for (int i = 0; i < 5; i++) {
            Circle circle = circles.get(i);
            Label number = numberLabels.get(i);
            Label label = stepLabels.get(i);
            HBox row = rows.get(i);

            circle.getStyleClass().removeAll(
                    "step-circle-pending", "step-circle-active", "step-circle-completed");
            number.getStyleClass().removeAll("step-number-light", "step-number-dark");
            label.getStyleClass().removeAll(
                    "step-label-pending", "step-label-active", "step-label-completed");

            if (i == activeStep) {
                circle.getStyleClass().add("step-circle-active");
                number.setText(String.valueOf(i + 1));
                number.getStyleClass().add("step-number-light");
                label.getStyleClass().add("step-label-active");
            } else if (completed[i]) {
                circle.getStyleClass().add("step-circle-completed");
                number.setText("✓");
                number.getStyleClass().add("step-number-light");
                label.getStyleClass().add("step-label-completed");
            } else {
                circle.getStyleClass().add("step-circle-pending");
                number.setText(String.valueOf(i + 1));
                number.getStyleClass().add("step-number-dark");
                label.getStyleClass().add("step-label-pending");
            }
            row.setCursor(Cursor.HAND);
        }
    }
}
