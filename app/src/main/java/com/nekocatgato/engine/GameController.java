package com.nekocatgato.engine;

import com.nekocatgato.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameController {
    public static final int BIG_BLIND = 20;
    public static final int SMALL_BLIND = 10;

    private final GameState state = new GameState();
    private final HandEvaluator evaluator = new HandEvaluator();
    private List<Player> players;
    private List<Player> activePlayers;
    private int dealerButtonIndex = -1;
    private boolean gameOver;
    private Player gameWinner;
    private GameEventListener listener;
    private volatile CompletableFuture<Void> nextRoundFuture;
    private List<Player> allOriginalPlayers;
    private int initialChipCount;
    private String lastRoundWinnerName;
    private int lastPotAmount;
    private final ExecutorService engineExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "EngineThread");
        t.setDaemon(true);
        return t;
    });

    public void setGameEventListener(GameEventListener listener) {
        this.listener = listener;
    }

    public void startGame(List<Player> players) {
        if (players == null) {
            throw new IllegalArgumentException("Player list cannot be null");
        }
        if (players.size() < 2) {
            throw new IllegalArgumentException("At least 2 players are required");
        }
        for (Player p : players) {
            if (p == null) {
                throw new IllegalArgumentException("Player list cannot contain null elements");
            }
            if (p.getChips() <= 0) {
                throw new IllegalArgumentException("All players must have more than 0 chips");
            }
        }
        this.players = new ArrayList<>(players);
        this.allOriginalPlayers = new ArrayList<>(players);
        this.initialChipCount = players.get(0).getChips();
        this.dealerButtonIndex = -1;
        this.gameOver = false;
        this.gameWinner = null;
        nextRound();
    }

    public void startGameAsync(List<Player> players) {
        startGame(players);
        validateSingleHumanPlayer(this.players);
        engineExecutor.submit(this::runGameLoop);
    }

    public void resetAndRestart() {
        if (!gameOver) {
            throw new IllegalStateException("Cannot reset while game is still running");
        }
        players = new ArrayList<>(allOriginalPlayers);
        for (Player p : players) {
            p.setChips(initialChipCount);
            p.getHand().clear();
            p.setCurrentBet(0);
        }
        gameOver = false;
        gameWinner = null;
        dealerButtonIndex = -1;
        nextRound();
        engineExecutor.submit(this::runGameLoop);
    }

    public void signalNextRound() {
        CompletableFuture<Void> f = nextRoundFuture;
        if (f != null && !f.isDone()) {
            f.complete(null);
        }
    }

    private void validateSingleHumanPlayer(List<Player> players) {
        long humanCount = players.stream().filter(p -> p instanceof HumanPlayer).count();
        if (humanCount != 1) {
            throw new IllegalArgumentException("Exactly one HumanPlayer required, got " + humanCount);
        }
    }
    ActionResult resolveAction(Player player, GameState state, int callAmount) {
        try {
            CompletableFuture<ActionResult> future = player.decideActionAsync(state, callAmount);
            return future.get();
        } catch (ExecutionException | CancellationException e) {
            return new ActionResult(Player.Action.FOLD, 0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GameLoopInterruptedException(e);
        }
    }

    public void nextRound() {
        assert players.size() >= 2 : "At least 2 players required";
        activePlayers = new ArrayList<>(players);
        state.getDeck().reset();
        state.getDeck().shuffle();
        state.getBoard().clear();
        state.resetPot();
        for (Player p : activePlayers) {
            p.getHand().clear();
            p.setCurrentBet(0);
        }
        state.setPhase(GameState.Phase.PRE_FLOP);
        dealerButtonIndex = (dealerButtonIndex + 1) % players.size();
        postBlinds();
        dealHoleCards();
    }

    private void postBlinds() {
        int sbIndex = smallBlindIndex();
        int bbIndex = bigBlindIndex();
        
        Player sbPlayer = players.get(sbIndex);
        Player bbPlayer = players.get(bbIndex);
        
        int sbAmount = Math.min(SMALL_BLIND, sbPlayer.getChips());
        int bbAmount = Math.min(BIG_BLIND, bbPlayer.getChips());
        
        collectBet(sbPlayer, sbAmount);
        collectBet(bbPlayer, bbAmount);
    }

    private void collectBet(Player player, int amount) {
        int actual = Math.min(amount, player.getChips());
        player.bet(actual);
        state.addToPot(actual);
    }

    private int getRaiseAmount(Player player) {
        if (player instanceof HumanPlayer) {
            return ((HumanPlayer) player).getPendingRaiseAmount();
        }
        // AI players don't raise in the current implementation
        return 0;
    }

    private int smallBlindIndex() {
        return (dealerButtonIndex + 1) % players.size();
    }

    private int bigBlindIndex() {
        return (dealerButtonIndex + 2) % players.size();
    }

    public void dealHoleCards() {
        if (state.getDeck().size() < activePlayers.size() * 2) {
            throw new IllegalStateException("Not enough cards in deck to deal hole cards");
        }
        for (int i = 0; i < 2; i++) {
            for (Player p : activePlayers) {
                p.getHand().addCard(state.getDeck().deal());
            }
        }
    }

    private void resetPlayerBets() {
        for (Player p : activePlayers) {
            p.setCurrentBet(0);
        }
    }

    private void runBettingRound(int firstToActIndex) {
        if (activePlayers.size() <= 1) {
            return;
        }

        int highestBet = 0;
        for (Player p : activePlayers) {
            highestBet = Math.max(highestBet, p.getCurrentBet());
        }

        Player lastRaiser = null;
        int pointer = firstToActIndex;
        int acted = 0;

        while (true) {
            if (activePlayers.size() == 1) {
                // Award pot to the last remaining player
                int pot = state.getPot();
                Player winner = activePlayers.get(0);
                winner.setChips(winner.getChips() + pot);
                state.resetPot();
                eliminateBrokePlayers();
                return;
            }

            int index = pointer % activePlayers.size();
            Player player = activePlayers.get(index);

            // Skip all-in players (chips == 0) without counting them as acted
            if (player.getChips() == 0) {
                pointer++;
                continue;
            }

            int callAmount = highestBet - player.getCurrentBet();
            Player.Action action = player.decideAction(state, callAmount);

            // Handle negative raise amounts as zero (requirement 10.4)
            // This is handled in the player's decideAction or we treat it here

            switch (action) {
                case FOLD:
                    activePlayers.remove(index);
                    if (activePlayers.size() == 1) {
                        // Award pot to the last remaining player
                        int pot = state.getPot();
                        Player winner = activePlayers.get(0);
                        winner.setChips(winner.getChips() + pot);
                        state.resetPot();
                        eliminateBrokePlayers();
                        return;
                    }
                    // Don't increment pointer - next player slides into this slot
                    break;

                case CHECK:
                    if (callAmount > 0) {
                        // Treat as CALL
                        collectBet(player, callAmount);
                    }
                    acted++;
                    pointer++;
                    break;

                case CALL:
                    collectBet(player, callAmount);
                    acted++;
                    pointer++;
                    break;

                case RAISE:
                    // Get raise amount from player
                    int raiseAmount = getRaiseAmount(player);
                    // Treat negative raise amounts as zero (requirement 10.4)
                    if (raiseAmount < 0) {
                        raiseAmount = 0;
                    }
                    if (raiseAmount < BIG_BLIND) {
                        // Treat as CALL
                        collectBet(player, callAmount);
                        acted++;
                        pointer++;
                    } else {
                        collectBet(player, raiseAmount);
                        highestBet = player.getCurrentBet();
                        lastRaiser = player;
                        acted = 1; // Reset - this player has acted
                        pointer++;
                    }
                    break;
            }

            // Termination: round ends when every active (non-all-in) player has acted
            // since the last raise, and no unmatched bet remains
            int eligibleCount = 0;
            for (Player p : activePlayers) {
                if (p.getChips() > 0) {
                    eligibleCount++;
                }
            }

            int currentIndex = pointer % activePlayers.size();
            Player currentPlayer = activePlayers.get(currentIndex);

            if (acted >= eligibleCount && (lastRaiser == null || currentPlayer == lastRaiser)) {
                break;
            }
        }
    }

    void runBettingRoundAsync(int firstToActIndex) {
        if (activePlayers.size() <= 1) {
            return;
        }

        int highestBet = 0;
        for (Player p : activePlayers) {
            highestBet = Math.max(highestBet, p.getCurrentBet());
        }

        Player lastRaiser = null;
        int pointer = firstToActIndex;
        int acted = 0;

        while (true) {
            if (activePlayers.size() == 1) {
                int pot = state.getPot();
                Player winner = activePlayers.get(0);
                lastPotAmount = pot;
                lastRoundWinnerName = winner.getName();
                winner.setChips(winner.getChips() + pot);
                state.resetPot();
                eliminateBrokePlayers();
                return;
            }

            int index = pointer % activePlayers.size();
            Player player = activePlayers.get(index);

            // Skip all-in players (0 chips) without calling decideActionAsync
            if (player.getChips() == 0) {
                pointer++;
                continue;
            }

            int callAmount = highestBet - player.getCurrentBet();

            // Notify listener before human turns
            if (player instanceof HumanPlayer && listener != null) {
                listener.onPlayerTurn(player, callAmount);
            }

            ActionResult result = resolveAction(player, state, callAmount);
            Player.Action action = result.action();

            // Notify listener after each action
            if (listener != null) {
                listener.onPlayerActed(player, action);
            }

            switch (action) {
                case FOLD:
                    activePlayers.remove(index);
                    if (activePlayers.size() == 1) {
                        int pot = state.getPot();
                        Player winner = activePlayers.get(0);
                        lastPotAmount = pot;
                        lastRoundWinnerName = winner.getName();
                        winner.setChips(winner.getChips() + pot);
                        state.resetPot();
                        eliminateBrokePlayers();
                        return;
                    }
                    break;

                case CHECK:
                    if (callAmount > 0) {
                        collectBet(player, callAmount);
                    }
                    acted++;
                    pointer++;
                    break;

                case CALL:
                    collectBet(player, callAmount);
                    acted++;
                    pointer++;
                    break;

                case RAISE:
                    int raiseAmount = result.raiseAmount();
                    if (raiseAmount < 0) {
                        raiseAmount = 0;
                    }
                    if (raiseAmount < BIG_BLIND) {
                        collectBet(player, callAmount);
                        acted++;
                        pointer++;
                    } else {
                        collectBet(player, raiseAmount);
                        highestBet = player.getCurrentBet();
                        lastRaiser = player;
                        acted = 1;
                        pointer++;
                    }
                    break;
            }

            int eligibleCount = 0;
            for (Player p : activePlayers) {
                if (p.getChips() > 0) {
                    eligibleCount++;
                }
            }

            int currentIndex = pointer % activePlayers.size();
            Player currentPlayer = activePlayers.get(currentIndex);

            if (acted >= eligibleCount && (lastRaiser == null || currentPlayer == lastRaiser)) {
                break;
            }
        }
    }

    public void dealFlop() {
        if (state.getPhase() != GameState.Phase.PRE_FLOP) {
            throw new IllegalStateException("dealFlop() requires PRE_FLOP phase, but current phase is " + state.getPhase());
        }
        for (int i = 0; i < 3; i++) {
            state.getBoard().addCard(state.getDeck().deal());
        }
        resetPlayerBets();
        state.setPhase(GameState.Phase.FLOP);
        runBettingRound((dealerButtonIndex + 1) % activePlayers.size());
    }

    public void dealTurn() {
        if (state.getPhase() != GameState.Phase.FLOP) {
            throw new IllegalStateException("dealTurn() requires FLOP phase, but current phase is " + state.getPhase());
        }
        state.getBoard().addCard(state.getDeck().deal());
        resetPlayerBets();
        state.setPhase(GameState.Phase.TURN);
        runBettingRound((dealerButtonIndex + 1) % activePlayers.size());
    }

    public void dealRiver() {
        if (state.getPhase() != GameState.Phase.TURN) {
            throw new IllegalStateException("dealRiver() requires TURN phase, but current phase is " + state.getPhase());
        }
        state.getBoard().addCard(state.getDeck().deal());
        resetPlayerBets();
        state.setPhase(GameState.Phase.RIVER);
        runBettingRound((dealerButtonIndex + 1) % activePlayers.size());
        if (activePlayers.size() > 1) {
            state.setPhase(GameState.Phase.SHOWDOWN);
        }
    }

    private List<Player> findWinners() {
        List<Player> winners = new ArrayList<>();
        HandEvaluator.HandResult bestResult = null;

        for (Player p : activePlayers) {
            List<Card> sevenCards = new ArrayList<>();
            sevenCards.addAll(p.getHand().getCards());
            sevenCards.addAll(state.getBoard().getCards());

            HandEvaluator.HandResult result = evaluator.evaluateBest(sevenCards);

            if (bestResult == null || result.compareTo(bestResult) > 0) {
                bestResult = result;
                winners.clear();
                winners.add(p);
            } else if (result.compareTo(bestResult) == 0) {
                winners.add(p);
            }
        }

        return winners;
    }

    private void awardPot(List<Player> winners) {
        int pot = state.getPot();
        if (winners.isEmpty() || pot == 0) {
            state.resetPot();
            return;
        }

        lastPotAmount = pot;
        lastRoundWinnerName = winners.size() == 1
                ? winners.get(0).getName()
                : winners.get(0).getName() + " (split)";

        int share = pot / winners.size();
        int remainder = pot % winners.size();

        for (Player w : winners) {
            w.setChips(w.getChips() + share);
        }
        // Remainder goes to first winner in seat order
        winners.get(0).setChips(winners.get(0).getChips() + remainder);

        state.resetPot();
    }

    /** Package-private for testability. Returns indices of eliminated players (before removal). */
    List<Integer> eliminateBrokePlayers() {
        List<Integer> eliminatedIndices = new ArrayList<>();
        List<Player> eliminatedPlayers = new ArrayList<>();

        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getChips() == 0) {
                eliminatedIndices.add(i);
                eliminatedPlayers.add(players.get(i));
            }
        }

        players.removeIf(p -> p.getChips() == 0);

        for (Player eliminated : eliminatedPlayers) {
            if (listener != null) {
                listener.onPlayerEliminated(eliminated);
            }
        }

        return eliminatedIndices;
    }

    boolean isHumanEliminated() {
        for (Player p : players) {
            if (p instanceof HumanPlayer) {
                return p.getChips() == 0;
            }
        }
        // Human not in the list — already removed
        return true;
    }

    /**
     * Adjusts the dealer button index after players have been removed from the list.
     * The eliminatedIndices are the original indices (before removal).
     * Must be called after players have already been removed from the list.
     */
    void adjustDealerIndexAfterElimination(List<Integer> eliminatedIndices) {
        if (eliminatedIndices.isEmpty() || players.isEmpty()) {
            return;
        }

        int beforeCount = 0;
        boolean dealerEliminated = false;

        for (int idx : eliminatedIndices) {
            if (idx < dealerButtonIndex) {
                beforeCount++;
            } else if (idx == dealerButtonIndex) {
                dealerEliminated = true;
            }
        }

        if (dealerEliminated) {
            // The player who sat after the dealer is now at (dealerButtonIndex - beforeCount)
            // in the shortened list. Set index to one less so nextRound()'s +1 lands on them.
            dealerButtonIndex = (dealerButtonIndex - beforeCount) - 1;
        } else {
            // Shift down to keep pointing at the same player in the shortened list.
            dealerButtonIndex -= beforeCount;
        }

        // Safety clamp: -1 is valid (nextRound handles it via (-1+1) % size = 0)
        dealerButtonIndex = Math.max(-1, dealerButtonIndex);
        if (players.size() > 0) {
            dealerButtonIndex = Math.min(dealerButtonIndex, players.size() - 1);
        }
    }

    public Player determineWinner() {
        if (state.getPhase() != GameState.Phase.SHOWDOWN) {
            throw new IllegalStateException("determineWinner() requires SHOWDOWN phase, but current phase is " + state.getPhase());
        }

        List<Player> winners = findWinners();
        awardPot(winners);

        return winners.get(0);
    }

    void runSingleHand() {
        // Pre-flop betting (blinds already posted, hole cards dealt by nextRound/startGame)
        notifyPhaseChanged(GameState.Phase.PRE_FLOP);
        int firstToAct = (dealerButtonIndex + 3) % activePlayers.size();
        runBettingRoundAsync(firstToAct);
        if (activePlayers.size() <= 1) { return; }

        // Flop
        for (int i = 0; i < 3; i++) {
            state.getBoard().addCard(state.getDeck().deal());
        }
        resetPlayerBets();
        state.setPhase(GameState.Phase.FLOP);
        notifyPhaseChanged(GameState.Phase.FLOP);
        runBettingRoundAsync((dealerButtonIndex + 1) % activePlayers.size());
        if (activePlayers.size() <= 1) { return; }

        // Turn
        state.getBoard().addCard(state.getDeck().deal());
        resetPlayerBets();
        state.setPhase(GameState.Phase.TURN);
        notifyPhaseChanged(GameState.Phase.TURN);
        runBettingRoundAsync((dealerButtonIndex + 1) % activePlayers.size());
        if (activePlayers.size() <= 1) { return; }

        // River
        state.getBoard().addCard(state.getDeck().deal());
        resetPlayerBets();
        state.setPhase(GameState.Phase.RIVER);
        notifyPhaseChanged(GameState.Phase.RIVER);
        runBettingRoundAsync((dealerButtonIndex + 1) % activePlayers.size());
        if (activePlayers.size() <= 1) { return; }

        // Showdown
        state.setPhase(GameState.Phase.SHOWDOWN);
        notifyPhaseChanged(GameState.Phase.SHOWDOWN);
        determineWinner();
    }

    void runGameLoop() {
        try {
            while (!gameOver) {
                runSingleHand();

                List<Integer> eliminatedIndices = eliminateBrokePlayers();

                if (isHumanEliminated() || players.size() == 1) {
                    gameOver = true;
                    gameWinner = players.size() == 1 ? players.get(0) : null;
                    notifyGameOver(gameWinner);
                    break;
                }

                notifyRoundComplete();

                nextRoundFuture = new CompletableFuture<>();
                nextRoundFuture.get(); // block until UI signals next round

                adjustDealerIndexAfterElimination(eliminatedIndices);
                nextRound();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            notifyError(new GameLoopInterruptedException(e));
        } catch (CancellationException e) {
            // Future cancelled during shutdown — exit silently
        } catch (ExecutionException e) {
            notifyError(e);
        } catch (GameLoopInterruptedException e) {
            notifyError(e);
        } catch (Exception e) {
            notifyError(e);
        }
    }

    private void notifyGameOver(Player winner) {
        if (listener != null) {
            listener.onGameOver(winner);
        }
    }

    private void notifyPhaseChanged(GameState.Phase phase) {
        if (listener != null) {
            listener.onPhaseChanged(phase, state);
        }
    }

    private void notifyRoundComplete() {
        if (listener != null) {
            listener.onRoundComplete(state);
        }
    }

    private void notifyError(Exception e) {
        if (listener != null) {
            listener.onError(e);
        }
    }

    public GameState getState() { return state; }
    public List<Player> getPlayers() { return players; }
    public List<Player> getActivePlayers() { return activePlayers; }
    public int getDealerButtonIndex() { return dealerButtonIndex; }
    public boolean isGameOver() { return gameOver; }
    public Player getGameWinner() { return gameWinner; }
    public List<Player> getAllOriginalPlayers() { return allOriginalPlayers; }
    public int getInitialChipCount() { return initialChipCount; }
    CompletableFuture<Void> getNextRoundFuture() { return nextRoundFuture; }
    public String getLastRoundWinnerName() { return lastRoundWinnerName; }
    public int getLastPotAmount() { return lastPotAmount; }
}
