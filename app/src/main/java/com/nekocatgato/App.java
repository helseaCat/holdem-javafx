package com.nekocatgato;

import com.nekocatgato.ui.MainMenuView;
import javafx.application.Application;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        new MainMenuView(stage).show();
    }

    public String getGreeting() {
        return "Hello, World!";
    };

    public static void main(String[] args) {
        launch(args);
    }
}
