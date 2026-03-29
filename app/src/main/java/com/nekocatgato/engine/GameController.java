package com.nekocatgato.engine;

import com.nekocatgato.model.*;
import java.util.ArrayList;
import java.util.List;

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
        this.dealerButtonIndex = -1;
        nextRound();
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
                awardPot(activePlayers);
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
                        awardPot(activePlayers);
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

    private void awardPot(List<Player> winners) {
        int pot = state.getPot();
        if (winners.isEmpty() || pot == 0) {
            state.resetPot();
            return;
        }

        int share = pot / winners.size();
        int remainder = pot % winners.size();

        for (Player w : winners) {
            w.setChips(w.getChips() + share);
        }
        // Remainder goes to first winner in seat order
        winners.get(0).setChips(winners.get(0).getChips() + remainder);

        state.resetPot();
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

    public Player determineWinner() {
        // TODO: fix bug - store best 5-card hand, not all 7 cards
        // TODO: handle ties - return List<Player> and split pot
        // TODO: handle all-in / side pots - track contributions, calculate main/side pots, distribute winnings
        Player best = null;
        List<Card> bestHand = null;

        for (Player p : activePlayers) {
            List<Card> allCards = new ArrayList<>();
            allCards.addAll(p.getHand().getCards());
            allCards.addAll(state.getBoard().getCards());

            if (best == null || evaluator.compare(allCards, bestHand) > 0) {
                best = p;
                bestHand = allCards;
            }
        }
        return best;
    }

    public GameState getState() { return state; }
    public List<Player> getPlayers() { return players; }
    public List<Player> getActivePlayers() { return activePlayers; }
    public int getDealerButtonIndex() { return dealerButtonIndex; }
    public boolean isGameOver() { return gameOver; }
    public Player getGameWinner() { return gameWinner; }
}
