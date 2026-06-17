package com.contextextractor.presentation.components;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

public class ToastNotification {

    public static void showSuccess(Node owner, String message) {
        show(owner, message, true);
    }

    public static void showError(Node owner, String message) {
        show(owner, message, false);
    }

    private static void show(Node owner, String message, boolean success) {
        Window window = owner.getScene().getWindow();

        Label label = new Label(message);
        label.setStyle(
                (success
                        ? "-fx-background-color: #10B981;"
                        : "-fx-background-color: #EF4444;")
                + "-fx-text-fill: white;"
                + "-fx-padding: 10 20 10 20;"
                + "-fx-background-radius: 6;"
                + "-fx-font-size: 13px;"
                + "-fx-font-weight: bold;");
        label.setPrefWidth(280);
        label.setWrapText(true);

        Popup popup = new Popup();
        popup.getContent().add(label);
        popup.setAutoHide(true);
        popup.show(window,
                window.getX() + (window.getWidth() - 280) / 2,
                window.getY() + window.getHeight() - 90);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), label);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        PauseTransition pause = new PauseTransition(Duration.millis(2500));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), label);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> popup.hide());

        new SequentialTransition(fadeIn, pause, fadeOut).play();
    }
}
