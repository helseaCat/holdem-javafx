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

    public void dealFlop() {
        for (int i = 0; i < 3; i++)
            state.getBoard().addCard(state.getDeck().deal());
        state.setPhase(GameState.Phase.FLOP);
    }

    public void dealTurn() {
        state.getBoard().addCard(state.getDeck().deal());
        state.setPhase(GameState.Phase.TURN);
    }

    public void dealRiver() {
        state.getBoard().addCard(state.getDeck().deal());
        state.setPhase(GameState.Phase.RIVER);
    }

    public Player determineWinner() {
        // TODO: filter to only active (non-folded) players
        // TODO: fix bug - store best 5-card hand, not all 7 cards
        // TODO: handle ties - return List<Player> and split pot
        // TODO: handle all-in / side pots - track contributions, calculate main/side pots, distribute winnings
        Player best = null;
        List<Card> bestHand = null;

        for (Player p : players) {
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
