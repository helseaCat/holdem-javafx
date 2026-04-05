package com.nekocatgato.ui;

import com.nekocatgato.engine.GameController;
import com.nekocatgato.engine.GameEventListener;
import com.nekocatgato.engine.HandEvaluator;
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
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
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
    private TextField raiseInput;
    private Button allInBtn;
    private Button nextRoundBtn;
    private HBox actionButtonsBox;

    private final Map<Player, VBox> playerCardAreas = new HashMap<>();
    private final Map<Player, HBox> playerCardBoxes = new HashMap<>();
    private final Map<Player, Text> betLabels = new HashMap<>();
    private final List<Player> allPlayers;

    private BorderPane root;
    private StackPane rootStack;
    private StackPane gameOverOverlay;
    private Text phaseText;
    private Text dealerButtonText;
    private Player highlightedPlayer;
    private boolean isShowdown = false;
    private final Map<Player, Text> handRankLabels = new HashMap<>();

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
        root = new BorderPane();

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

        foldBtn.setOnAction(e -> submitPlayerAction(Player.Action.FOLD, 0));
        checkBtn.setOnAction(e -> submitPlayerAction(Player.Action.CHECK, 0));
        callBtn.setOnAction(e -> submitPlayerAction(Player.Action.CALL, 0));
        raiseBtn.setOnAction(e -> submitPlayerAction(Player.Action.RAISE, 0)); // raiseAmount param ignored for RAISE; read from raiseInput

        raiseInput = new TextField(String.valueOf(GameController.BIG_BLIND));
        raiseInput.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("\\d*") ? change : null));

        allInBtn = new Button("All In");
        setActionButtonsDisabled(true);
        allInBtn.setOnAction(e -> {
            raiseInput.setText(String.valueOf(humanPlayer.getChips()));
            submitPlayerAction(Player.Action.RAISE, humanPlayer.getChips());
        });

        nextRoundBtn = new Button("Next Round");
        nextRoundBtn.setVisible(false);
        nextRoundBtn.setManaged(false);
        nextRoundBtn.setOnAction(e -> gameController.signalNextRound());

        // Pot, phase, and status display
        potText = new Text("Pot: $0");
        phaseText = new Text("");
        statusText = new Text("");
        dealerButtonText = new Text("Ⓓ");
        dealerButtonText.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-fill: #d4af37;");

        buildPlayerAreas(root);

        rootStack = new StackPane(root);
        stage.setScene(new Scene(rootStack, 1024, 768));
        stage.setTitle("Texas Hold'em — Table");
        stage.show();

        // Wire up listener and start the async game loop
        gameController.setGameEventListener(this);
        gameController.startGameAsync(players);
    }

    private void buildPlayerAreas(BorderPane root) {
        playerCardAreas.clear();
        playerCardBoxes.clear();
        betLabels.clear();

        List<Player> aiPlayers = new ArrayList<>();
        for (Player p : allPlayers) {
            // Create a VBox for each player: name, chips, bet label, card HBox
            Text nameText = new Text(p.getName());
            Text chipText = new Text("Chips: $" + p.getChips());

            Text betLabel = new Text();
            betLabel.setVisible(false);
            betLabels.put(p, betLabel);

            HBox cardBox = new HBox(5);
            cardBox.setAlignment(Pos.CENTER);

            VBox playerBox = new VBox(5, nameText, chipText, betLabel, cardBox);
            playerBox.setAlignment(Pos.CENTER);
            playerBox.setStyle("-fx-padding: 10;");

            playerCardAreas.put(p, playerBox);
            playerCardBoxes.put(p, cardBox);

            if (!(p instanceof HumanPlayer)) {
                aiPlayers.add(p);
            }
        }

        // Bottom: human player's card area + action buttons
        if (humanPlayer != null) {
            VBox humanBox = playerCardAreas.get(humanPlayer);
            actionButtonsBox = new HBox(10, foldBtn, checkBtn, callBtn, raiseBtn, raiseInput, allInBtn);
            actionButtonsBox.setAlignment(Pos.CENTER);
            actionButtonsBox.setStyle("-fx-padding: 10;");

            HBox actionsArea = new HBox(10, actionButtonsBox, nextRoundBtn);
            actionsArea.setAlignment(Pos.CENTER);
            actionsArea.setStyle("-fx-padding: 10;");

            VBox bottomArea = new VBox(10, humanBox, actionsArea);
            bottomArea.setAlignment(Pos.CENTER);
            root.setBottom(bottomArea);
        }

        // Distribute AI players across top, left, and right
        // Top gets pot/status bar + first AI player(s)
        VBox topBar = new VBox(5, potText, phaseText, statusText);
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
            applyTurnHighlight(player);
            statusText.setText(player.getName() + "'s turn — call amount: $" + callAmount);
            setActionButtonsDisabled(false);
            updateRaiseInputState(humanPlayer.getChips());
        });
    }

    @Override
    public void onPlayerActed(Player player, Player.Action action) {
        Platform.runLater(() -> {
            removeTurnHighlight();
            statusText.setText(player.getName() + ": " + action);
            setActionButtonsDisabled(true);
            if (action == Player.Action.FOLD) {
                removePlayerCards(player);
            }
            updateBetDisplays();
            updatePotDisplay(gameController.getState().getPot());
            updateChipDisplays();
        });
    }

    @Override
    public void onPhaseChanged(GameState.Phase phase, GameState state) {
        Platform.runLater(() -> {
            updatePhaseDisplay(phase);
            updatePotDisplay(state.getPot());
            updateBetDisplays();
            if (phase == GameState.Phase.SHOWDOWN) {
                isShowdown = true;
            }
            if (phase == GameState.Phase.PRE_FLOP) {
                isShowdown = false;
                clearHandRankLabels();
                // Restore action buttons, hide "Next Round" button
                nextRoundBtn.setVisible(false);
                nextRoundBtn.setManaged(false);
                actionButtonsBox.setVisible(true);
                actionButtonsBox.setManaged(true);
                setActionButtonsDisabled(true);
                updateDealerButton();
            }
            updateBoardDisplay(state);
            updateHoleCardDisplay(phase);
            updateChipDisplays();
            statusText.setText(phase.toString());
        });
    }

    @Override
    public void onRoundComplete(GameState state) {
        Platform.runLater(() -> {
            removeTurnHighlight();
            phaseText.setText("");
            updatePotDisplay(state.getPot());
            for (Text betLabel : betLabels.values()) {
                betLabel.setVisible(false);
            }
            if (dealerButtonText.getParent() instanceof VBox parent) {
                parent.getChildren().remove(dealerButtonText);
            }

            // Show round result: winner name and pot amount
            String winnerName = gameController.getLastRoundWinnerName();
            int potAmount = gameController.getLastPotAmount();
            if (winnerName != null) {
                statusText.setText(winnerName + " wins $" + potAmount);
            } else {
                statusText.setText("Round complete");
            }

            updateChipDisplays();
            if (!isShowdown) {
                clearAllCardDisplays();
            }

            // Swap action buttons for "Next Round" button
            actionButtonsBox.setVisible(false);
            actionButtonsBox.setManaged(false);
            nextRoundBtn.setVisible(true);
            nextRoundBtn.setManaged(true);
        });
    }

    @Override
    public void onPlayerEliminated(Player player) {
        Platform.runLater(() -> {
            VBox playerBox = playerCardAreas.get(player);
            if (playerBox != null && playerBox.getParent() instanceof javafx.scene.layout.Pane parent) {
                parent.getChildren().remove(playerBox);
            }
            playerCardAreas.remove(player);
            playerCardBoxes.remove(player);
            betLabels.remove(player);
        });
    }

    @Override
    public void onGameOver(Player winner) {
        Platform.runLater(() -> {
            setActionButtonsDisabled(true);

            // Hide next round button if visible
            nextRoundBtn.setVisible(false);
            nextRoundBtn.setManaged(false);

            // Build overlay
            gameOverOverlay = new StackPane();
            gameOverOverlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.75);");

            Text messageText;
            if (winner != null) {
                messageText = new Text(winner.getName() + " wins with $" + winner.getChips());
            } else {
                messageText = new Text("You have been eliminated");
            }
            messageText.setFont(Font.font(32));
            messageText.setStyle("-fx-fill: white;");

            VBox overlayContent = new VBox(20, messageText);
            overlayContent.setAlignment(Pos.CENTER);

            Button playAgainBtn = new Button("Play Again");
            playAgainBtn.setStyle("-fx-font-size: 18px; -fx-padding: 10 30;");
            playAgainBtn.setOnAction(e -> {
                rootStack.getChildren().remove(gameOverOverlay);
                gameOverOverlay = null;
                allPlayers.clear();
                allPlayers.addAll(gameController.getAllOriginalPlayers());
                buildPlayerAreas(root);
                setActionButtonsDisabled(true);
                clearAllCardDisplays();
                updatePotDisplay(0);
                statusText.setText("");
                phaseText.setText("");
                gameController.resetAndRestart();
            });
            overlayContent.getChildren().add(playAgainBtn);

            gameOverOverlay.getChildren().add(overlayContent);
            rootStack.getChildren().add(gameOverOverlay);
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

    // ---- Validation & State Helpers ----

    /**
     * Validates and clamps a raise amount string.
     * Returns the validated int in [BIG_BLIND, chips], or -1 if invalid.
     * Package-private and static for direct unit testing without JavaFX.
     */
    static int validateRaiseAmount(String text, int chips) {
        if (text == null || text.isEmpty()) {
            return -1;
        }
        int amount;
        try {
            amount = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return -1;
        }
        if (amount < GameController.BIG_BLIND) {
            return -1;
        }
        if (amount > chips) {
            return chips;
        }
        return amount;
    }

    /**
     * Updates the raise input, raise button, and all-in button state
     * based on the player's current chip count.
     * Package-private for testability.
     */
    void updateRaiseInputState(int playerChips) {
        if (playerChips < GameController.BIG_BLIND) {
            raiseBtn.setDisable(true);
            allInBtn.setDisable(true);
            raiseInput.setDisable(true);
        } else if (playerChips == GameController.BIG_BLIND) {
            raiseBtn.setDisable(false);
            allInBtn.setDisable(false);
            raiseInput.setDisable(false);
            raiseInput.setEditable(false);
            raiseInput.setText(String.valueOf(GameController.BIG_BLIND));
        } else {
            raiseBtn.setDisable(false);
            allInBtn.setDisable(false);
            raiseInput.setDisable(false);
            raiseInput.setEditable(true);
            raiseInput.setText(String.valueOf(GameController.BIG_BLIND));
        }
    }

    // ---- Helpers ----

    private void submitPlayerAction(Player.Action action, int raiseAmount) {
        if (humanPlayer != null) {
            if (action == Player.Action.RAISE) {
                int validated = validateRaiseAmount(raiseInput.getText(), humanPlayer.getChips());
                if (validated == -1) {
                    raiseInput.requestFocus();
                    return;
                }
                setActionButtonsDisabled(true);
                humanPlayer.submitAction(action, validated);
            } else {
                setActionButtonsDisabled(true);
                humanPlayer.submitAction(action, raiseAmount);
            }
        }
    }

    private void setActionButtonsDisabled(boolean disabled) {
        foldBtn.setDisable(disabled);
        checkBtn.setDisable(disabled);
        callBtn.setDisable(disabled);
        raiseBtn.setDisable(disabled);
        raiseInput.setDisable(disabled);
        allInBtn.setDisable(disabled);
    }

    private void updateBoardDisplay(GameState state) {
        boardArea.getChildren().clear();
        for (Card card : state.getBoard().getCards()) {
            boardArea.getChildren().add(new CardView(card));
        }
    }

    private void removePlayerCards(Player player) {
        HBox cardBox = playerCardBoxes.get(player);
        if (cardBox == null) return;
        cardBox.getChildren().clear();
    }

    private void updateChipDisplays() {
        for (Map.Entry<Player, VBox> entry : playerCardAreas.entrySet()) {
            Player player = entry.getKey();
            VBox playerBox = entry.getValue();
            // Chip text is the second child (index 1) of the VBox
            Text chipText = (Text) playerBox.getChildren().get(1);
            chipText.setText("Chips: $" + player.getChips());
        }
    }

    private void updateBetDisplays() {
        for (Map.Entry<Player, Text> entry : betLabels.entrySet()) {
            Player player = entry.getKey();
            Text betLabel = entry.getValue();
            int bet = player.getCurrentBet();
            if (bet > 0) {
                betLabel.setText("Bet: $" + bet);
                betLabel.setVisible(true);
            } else {
                betLabel.setVisible(false);
            }
        }
    }

    private void updateDealerButton() {
        // Remove from current parent (if any)
        if (dealerButtonText.getParent() instanceof VBox parent) {
            parent.getChildren().remove(dealerButtonText);
        }

        List<Player> allPlayersList = gameController.getPlayers();
        int dealerIndex = gameController.getDealerButtonIndex();

        // Defensive bounds check
        if (dealerIndex < 0 || dealerIndex >= allPlayersList.size()) {
            return;
        }

        Player dealer = allPlayersList.get(dealerIndex);
        VBox dealerBox = playerCardAreas.get(dealer);
        if (dealerBox != null) {
            dealerBox.getChildren().add(dealerButtonText);
        }
    }

    private void applyTurnHighlight(Player player) {
        removeTurnHighlight();
        VBox playerBox = playerCardAreas.get(player);
        if (playerBox != null) {
            playerBox.setStyle("-fx-padding: 10; -fx-border-color: gold; -fx-border-width: 2;");
            highlightedPlayer = player;
        }
    }

    private void removeTurnHighlight() {
        if (highlightedPlayer != null) {
            VBox box = playerCardAreas.get(highlightedPlayer);
            if (box != null) {
                box.setStyle("-fx-padding: 10;");
            }
            highlightedPlayer = null;
        }
    }

    private void updatePhaseDisplay(GameState.Phase phase) {
        String label = switch (phase) {
            case PRE_FLOP -> "PRE-FLOP";
            case FLOP -> "FLOP";
            case TURN -> "TURN";
            case RIVER -> "RIVER";
            case SHOWDOWN -> "SHOWDOWN";
        };
        phaseText.setText(label);
    }

    private void updatePotDisplay(int potAmount) {
        potText.setText("Pot: $" + potAmount);
    }

    private void clearAllCardDisplays() {
        for (Map.Entry<Player, HBox> entry : playerCardBoxes.entrySet()) {
            entry.getValue().getChildren().clear();
        }
        boardArea.getChildren().clear();
        clearHandRankLabels();
    }

    private void clearHandRankLabels() {
        for (Map.Entry<Player, Text> entry : handRankLabels.entrySet()) {
            Text label = entry.getValue();
            if (label.getParent() instanceof VBox parent) {
                parent.getChildren().remove(label);
            }
        }
        handRankLabels.clear();
    }

    private void updateHoleCardDisplay(GameState.Phase phase) {
        if (phase == GameState.Phase.PRE_FLOP) {
            for (Player player : allPlayers) {
                HBox cardBox = playerCardBoxes.get(player);
                if (cardBox == null) continue;

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
            List<Card> boardCards = gameController.getState().getBoard().getCards();
            List<Player> showdownPlayers = gameController.getActivePlayers();
            for (Player player : showdownPlayers) {
                if (!(player instanceof AIPlayer)) continue;

                HBox cardBox = playerCardBoxes.get(player);
                if (cardBox == null) continue;

                List<Card> holeCards = player.getHand().getCards();
                if (holeCards.isEmpty()) continue;

                cardBox.getChildren().clear();
                for (Card card : holeCards) {
                    cardBox.getChildren().add(new CardView(card));
                }
            }

            // Add hand rank labels only for players participating in showdown
            for (Player player : showdownPlayers) {
                List<Card> holeCards = player.getHand().getCards();
                if (holeCards.isEmpty()) continue;

                List<Card> allCards = new ArrayList<>(holeCards);
                allCards.addAll(boardCards);
                if (allCards.size() >= 5) {
                    HandEvaluator.HandResult result = new HandEvaluator().evaluateBest(allCards);
                    Text rankLabel = new Text(formatHandRank(result.rank()));
                    rankLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
                    VBox playerArea = playerCardAreas.get(player);
                    if (playerArea != null) {
                        playerArea.getChildren().add(rankLabel);
                        handRankLabels.put(player, rankLabel);
                    }
                }
            }
        }
    }

    static String formatHandRank(HandEvaluator.HandRank rank) {
        String[] words = rank.name().split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(words[i].charAt(0));
            sb.append(words[i].substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
