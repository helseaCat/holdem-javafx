package com.nekocatgato.ui;

import com.nekocatgato.engine.GameEventListener;
import com.nekocatgato.model.Card;
import com.nekocatgato.model.GameState;
import com.nekocatgato.model.Player;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class GameTableView implements GameEventListener {
    private final Stage stage;

    private HBox boardArea;
    private Text potText;
    private Text statusText;
    private Button foldBtn;
    private Button checkBtn;
    private Button callBtn;
    private Button raiseBtn;

    public GameTableView(Stage stage) {
        this.stage = stage;
    }

    public void show() {
        BorderPane root = new BorderPane();

        // Community cards area
        boardArea = new HBox(10);
        boardArea.setAlignment(Pos.CENTER);
        boardArea.setStyle("-fx-padding: 20;");
        root.setCenter(boardArea);

        // Action buttons (disabled until it's the human player's turn)
        foldBtn = new Button("Fold");
        checkBtn = new Button("Check");
        callBtn = new Button("Call");
        raiseBtn = new Button("Raise");
        setActionButtonsDisabled(true);

        HBox actions = new HBox(10, foldBtn, checkBtn, callBtn, raiseBtn);
        actions.setAlignment(Pos.CENTER);
        actions.setStyle("-fx-padding: 10;");
        root.setBottom(actions);

        // Pot and status display
        potText = new Text("Pot: $0");
        statusText = new Text("");
        VBox topBar = new VBox(5, potText, statusText);
        topBar.setAlignment(Pos.CENTER);
        root.setTop(topBar);

        stage.setScene(new Scene(root, 1024, 768));
        stage.setTitle("Texas Hold'em — Table");
        stage.show();
    }

    // ---- GameEventListener implementation ----

    @Override
    public void onPlayerTurn(Player player, int callAmount) {
        Platform.runLater(() -> {
            statusText.setText(player.getName() + "'s turn — call amount: $" + callAmount);
            setActionButtonsDisabled(false);
        });
    }

    @Override
    public void onPlayerActed(Player player, Player.Action action) {
        Platform.runLater(() -> {
            statusText.setText(player.getName() + ": " + action);
            setActionButtonsDisabled(true);
        });
    }

    @Override
    public void onPhaseChanged(GameState.Phase phase, GameState state) {
        Platform.runLater(() -> {
            potText.setText("Pot: $" + state.getPot());
            updateBoardDisplay(state);
            statusText.setText(phase.toString());
        });
    }

    @Override
    public void onRoundComplete(GameState state) {
        Platform.runLater(() -> {
            potText.setText("Pot: $" + state.getPot());
            statusText.setText("Round complete");
        });
    }

    @Override
    public void onError(Exception e) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Game Error");
            alert.setHeaderText("An error occurred");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        });
    }

    // ---- Helpers ----

    private void setActionButtonsDisabled(boolean disabled) {
        foldBtn.setDisable(disabled);
        checkBtn.setDisable(disabled);
        callBtn.setDisable(disabled);
        raiseBtn.setDisable(disabled);
    }

    private void updateBoardDisplay(GameState state) {
        boardArea.getChildren().clear();
        for (Card card : state.getBoard().getCards()) {
            boardArea.getChildren().add(new CardView(card));
        }
    }
}
