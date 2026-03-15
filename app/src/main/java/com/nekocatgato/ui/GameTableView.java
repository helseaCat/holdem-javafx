package com.nekocatgato.ui;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class GameTableView {
    private final Stage stage;

    public GameTableView(Stage stage) {
        this.stage = stage;
    }

    public void show() {
        BorderPane root = new BorderPane();

        // Community cards area
        HBox boardArea = new HBox(10);
        boardArea.setAlignment(Pos.CENTER);
        boardArea.setStyle("-fx-padding: 20;");
        root.setCenter(boardArea);

        // Action buttons
        Button foldBtn = new Button("Fold");
        Button checkBtn = new Button("Check");
        Button callBtn = new Button("Call");
        Button raiseBtn = new Button("Raise");

        HBox actions = new HBox(10, foldBtn, checkBtn, callBtn, raiseBtn);
        actions.setAlignment(Pos.CENTER);
        actions.setStyle("-fx-padding: 10;");
        root.setBottom(actions);

        // Pot display
        Text potText = new Text("Pot: $0");
        VBox topBar = new VBox(potText);
        topBar.setAlignment(Pos.CENTER);
        root.setTop(topBar);

        stage.setScene(new Scene(root, 1024, 768));
        stage.setTitle("Texas Hold'em — Table");
        stage.show();
    }
}
