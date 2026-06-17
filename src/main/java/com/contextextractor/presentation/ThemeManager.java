package com.contextextractor.presentation;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.Scene;

public final class ThemeManager {

    private static final String THEME_CSS =
            ThemeManager.class.getResource("/styles/theme.css").toExternalForm();
    private static final String DARK_CSS =
            ThemeManager.class.getResource("/styles/theme-dark.css").toExternalForm();

    private ThemeManager() {}

    public static void apply(Scene scene, boolean darkMode) {
        if (darkMode) {
            Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        } else {
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        }
        scene.getStylesheets().clear();
        scene.getStylesheets().add(THEME_CSS);
        if (darkMode) {
            scene.getStylesheets().add(DARK_CSS);
        }
    }
}
