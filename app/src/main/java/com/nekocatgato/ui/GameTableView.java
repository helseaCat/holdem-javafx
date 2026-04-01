package com.nekocatgato.ui;

import com.nekocatgato.engine.GameController;
import com.nekocatgato.engine.GameEventListener;
import com.nekocatgato.model.AIPlayer;
import com.nekocatgato.model.Card;
import com.nekocatgato.model.GameState;
import com.nekocatgato.model.HumanPlayer;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameTableView implements GameEventListener {
    private final Stage stage;
    private final GameController gameController;
    private final List<Player> players;
    private HumanPlayer humanPlayer;

    private HBox boardArea;
    private Text potText;
    private Text statusText;
    private Button foldBtn;
    private Button checkBtn;
    private Button callBtn;
    private Button raiseBtn;

    private final Map<Player, VBox> playerCardAreas = new HashMap<>();
    private final List<Player> allPlayers;

    public GameTableView(Stage stage, GameController gameController, List<Player> players) {
        this.stage = stage;
        this.gameController = gameController;
        this.players = players;
        this.allPlayers = new ArrayList<>(players);
        // Find the HumanPlayer from the list
        for (Player p : players) {
            if (p instanceof HumanPlayer hp) {
                this.humanPlayer = hp;
                break;
            }
        }
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

        foldBtn.setOnAction(e -> submitPlayerAction(Player.Action.FOLD, 0));
        checkBtn.setOnAction(e -> submitPlayerAction(Player.Action.CHECK, 0));
        callBtn.setOnAction(e -> submitPlayerAction(Player.Action.CALL, 0));
        raiseBtn.setOnAction(e -> submitPlayerAction(Player.Action.RAISE, 0));

        // Pot and status display
        potText = new Text("Pot: $0");
        statusText = new Text("");

        buildPlayerAreas(root);

        stage.setScene(new Scene(root, 1024, 768));
        stage.setTitle("Texas Hold'em — Table");
        stage.show();

        // Wire up listener and start the async game loop
        gameController.setGameEventListener(this);
        gameController.startGameAsync(players);
    }

    private void buildPlayerAreas(BorderPane root) {
        playerCardAreas.clear();

        List<Player> aiPlayers = new ArrayList<>();
        for (Player p : allPlayers) {
            // Create a VBox for each player: name, chips, card HBox
            Text nameText = new Text(p.getName());
            Text chipText = new Text("Chips: $" + p.getChips());
            HBox cardBox = new HBox(5);
            cardBox.setAlignment(Pos.CENTER);

            VBox playerBox = new VBox(5, nameText, chipText, cardBox);
            playerBox.setAlignment(Pos.CENTER);
            playerBox.setStyle("-fx-padding: 10;");

            playerCardAreas.put(p, playerBox);

            if (!(p instanceof HumanPlayer)) {
                aiPlayers.add(p);
            }
        }

        // Bottom: human player's card area + action buttons
        if (humanPlayer != null) {
            VBox humanBox = playerCardAreas.get(humanPlayer);
            HBox actions = new HBox(10, foldBtn, checkBtn, callBtn, raiseBtn);
            actions.setAlignment(Pos.CENTER);
            actions.setStyle("-fx-padding: 10;");

            VBox bottomArea = new VBox(10, humanBox, actions);
            bottomArea.setAlignment(Pos.CENTER);
            root.setBottom(bottomArea);
        }

        // Distribute AI players across top, left, and right
        // Top gets pot/status bar + first AI player(s)
        VBox topBar = new VBox(5, potText, statusText);
        topBar.setAlignment(Pos.CENTER);

        if (!aiPlayers.isEmpty()) {
            // First AI player goes to top (alongside pot/status)
            HBox topPlayers = new HBox(20);
            topPlayers.setAlignment(Pos.CENTER);
            topPlayers.getChildren().add(playerCardAreas.get(aiPlayers.get(0)));

            // If there are 4+ AI players, put the second one at top too
            if (aiPlayers.size() >= 4) {
                topPlayers.getChildren().add(playerCardAreas.get(aiPlayers.get(1)));
            }

            VBox topArea = new VBox(10, topBar, topPlayers);
            topArea.setAlignment(Pos.CENTER);
            root.setTop(topArea);

            // Distribute remaining AI players to left and right
            int nextIndex = (aiPlayers.size() >= 4) ? 2 : 1;

            if (nextIndex < aiPlayers.size()) {
                VBox leftArea = new VBox(10);
                leftArea.setAlignment(Pos.CENTER);
                leftArea.getChildren().add(playerCardAreas.get(aiPlayers.get(nextIndex)));
                root.setLeft(leftArea);
                nextIndex++;
            }

            if (nextIndex < aiPlayers.size()) {
                VBox rightArea = new VBox(10);
                rightArea.setAlignment(Pos.CENTER);
                rightArea.getChildren().add(playerCardAreas.get(aiPlayers.get(nextIndex)));
                root.setRight(rightArea);
            }
        } else {
            root.setTop(topBar);
        }
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
            updateHoleCardDisplay(phase);
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

    private void submitPlayerAction(Player.Action action, int raiseAmount) {
        if (humanPlayer != null) {
            setActionButtonsDisabled(true);
            humanPlayer.submitAction(action, raiseAmount);
        }
    }

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

    private void updateHoleCardDisplay(GameState.Phase phase) {
        if (phase == GameState.Phase.PRE_FLOP) {
            for (Player player : allPlayers) {
                VBox playerBox = playerCardAreas.get(player);
                if (playerBox == null) continue;

                // The card HBox is the last child of the VBox
                HBox cardBox = (HBox) playerBox.getChildren().getLast();
                cardBox.getChildren().clear();

                List<Card> holeCards = player.getHand().getCards();
                if (player instanceof HumanPlayer) {
                    for (Card card : holeCards) {
                        cardBox.getChildren().add(new CardView(card));
                    }
                } else if (player instanceof AIPlayer) {
                    for (int i = 0; i < holeCards.size(); i++) {
                        cardBox.getChildren().add(new CardView());
                    }
                }
            }
        } else if (phase == GameState.Phase.SHOWDOWN) {
            for (Player player : allPlayers) {
                if (!(player instanceof AIPlayer)) continue;

                VBox playerBox = playerCardAreas.get(player);
                if (playerBox == null) continue;

                HBox cardBox = (HBox) playerBox.getChildren().getLast();
                List<Card> holeCards = player.getHand().getCards();
                if (holeCards.isEmpty()) continue;

                cardBox.getChildren().clear();
                for (Card card : holeCards) {
                    cardBox.getChildren().add(new CardView(card));
                }
            }
        }
    }
}
