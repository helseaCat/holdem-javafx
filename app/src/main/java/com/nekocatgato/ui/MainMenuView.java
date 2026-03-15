package com.nekocatgato.ui;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class MainMenuView {
    private final Stage stage;

    public MainMenuView(Stage stage) {
        this.stage = stage;
    }

    public void show() {
        Text title = new Text("Texas Hold'em");
        title.setStyle("-fx-font-size: 32px;");

        Button startBtn = new Button("Start Game");
        startBtn.setOnAction(e -> new GameTableView(stage).show());

        VBox layout = new VBox(20, title, startBtn);
        layout.setAlignment(Pos.CENTER);

        stage.setScene(new Scene(layout, 800, 600));
        stage.setTitle("Texas Hold'em");
        stage.show();
    }
}
