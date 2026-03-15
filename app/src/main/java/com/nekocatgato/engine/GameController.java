package com.nekocatgato.engine;

import com.nekocatgato.model.*;
import java.util.List;

public class GameController {
    private final GameState state = new GameState();
    private final HandEvaluator evaluator = new HandEvaluator();
    private List<Player> players;

    public void startGame(List<Player> players) {
        this.players = players;
        nextRound();
    }

    public void nextRound() {
        state.getDeck().reset();
        state.getDeck().shuffle();
        state.getBoard().clear();
        state.resetPot();
        state.setPhase(GameState.Phase.PRE_FLOP);
        players.forEach(p -> p.getHand().clear());
        dealHoleCards();
    }

    private void dealHoleCards() {
        for (int i = 0; i < 2; i++)
            for (Player p : players)
                p.getHand().addCard(state.getDeck().deal());
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
        // TODO: use HandEvaluator to find winner among active players
        return players.get(0);
    }

    public GameState getState() { return state; }
    public List<Player> getPlayers() { return players; }
}
