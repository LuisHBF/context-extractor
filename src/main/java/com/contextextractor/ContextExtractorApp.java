package com.contextextractor;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.contextextractor.domain.model.AppSettings;
import com.contextextractor.infrastructure.persistence.SettingsRepository;
import com.contextextractor.presentation.MainController;
import com.contextextractor.presentation.ThemeManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class ContextExtractorApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        AppSettings settings = new SettingsRepository().load();
        Application.setUserAgentStylesheet(
                settings.darkMode()
                        ? new PrimerDark().getUserAgentStylesheet()
                        : new PrimerLight().getUserAgentStylesheet());

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Scene scene = new Scene(loader.load(), 1280, 800);
        ThemeManager.apply(scene, settings.darkMode());

        MainController controller = loader.getController();
        controller.setScene(scene);

        stage.setTitle("Context Extractor");
        stage.setMinWidth(1100);
        stage.setMinHeight(700);
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/assets/logo-256.png")));
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/assets/logo-64.png")));
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
