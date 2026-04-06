package com.nekocatgato.ui;

import com.nekocatgato.engine.GameController;
import com.nekocatgato.model.AIPlayer;
import com.nekocatgato.model.HumanPlayer;
import com.nekocatgato.model.Player;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.util.List;

public class MainMenuView {
    private final Stage stage;

    public MainMenuView(Stage stage) {
        this.stage = stage;
    }

    public void show() {
        Text title = new Text("Texas Hold'em");
        title.setStyle("-fx-font-size: 32px;");

        Button startBtn = new Button("Start Game");
        startBtn.setOnAction(e -> startGame());

        VBox layout = new VBox(20, title, startBtn);
        layout.setAlignment(Pos.CENTER);

        stage.setScene(new Scene(layout, 800, 600));
        stage.setTitle("Texas Hold'em");
        stage.show();
    }

    private void startGame() {
        HumanPlayer human = new HumanPlayer("You", 1000);
        List<Player> players = List.of(
            human,
            new AIPlayer("Bob", 1000),
            new AIPlayer("Alice", 1000),
            new AIPlayer("Charlie", 1000)
        );

        GameController controller = new GameController();
        new GameTableView(stage, controller, players).show();
    }
}
