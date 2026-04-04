package com.nekocatgato.engine;

// Feature: multi-round-game, Property 8: Game over notification fires with correct winner

import com.nekocatgato.model.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;

/**
 * Property 8: Game over notification fires with correct winner
 *
 * For any game that reaches the game-over state, the GameEventListener shall
 * receive exactly one onGameOver(Player) call. When one player remains, the
 * argument shall be that player. When the human player is eliminated
 * (Requirement 6.1), the argument shall be null.
 *
 * Validates: Requirements 3.2
 */
class GameOverNotificationPropertyTest {

    /** Tracking listener that records onGameOver calls (count and argument). */
    static class TrackingListener implements GameEventListener {
        final List<Player> gameOverCalls = new ArrayList<>();

        @Override public void onPhaseChanged(GameState.Phase phase, GameState state) {}
        @Override public void onPlayerTurn(Player player, int callAmount) {}
        @Override public void onPlayerActed(Player player, Player.Action action) {}
        @Override public void onRoundComplete(GameState state) {}
        @Override public void onPlayerEliminated(Player player) {}
        @Override public void onError(Exception e) {}

        @Override
        public void onGameOver(Player winner) {
            gameOverCalls.add(winner);
        }
    }

    /** Simple concrete Player for testing — always checks/calls. */
    static class TestPlayer extends Player {
        TestPlayer(String name, int chips) {
            super(name, chips);
        }

        @Override
        public Action decideAction(GameState state, int callAmount) {
            return callAmount == 0 ? Action.CHECK : Action.CALL;
        }
    }

    /**
     * Validates: Requirements 3.2
     *
     * Scenario 1: One player remains (winner != null).
     * Call notifyGameOver with a surviving player, verify the listener receives
     * exactly one onGameOver call with that player.
     */
    @Property(tries = 100)
    void gameOverNotificationFiresWithCorrectWinner(
            @ForAll @IntRange(min = 2, max = 6) int playerCount,
            @ForAll @IntRange(min = 100, max = 10000) int winnerChips) {

        // Build player list
        List<Player> playerList = new ArrayList<>();
        playerList.add(new HumanPlayer("Human", winnerChips));
        for (int i = 1; i < playerCount; i++) {
            playerList.add(new TestPlayer("AI_" + i, 1000));
        }

        TrackingListener tracker = new TrackingListener();
        GameController gc = new GameController();
        gc.setGameEventListener(tracker);
        gc.startGame(playerList);

        // Pick the surviving player (the human)
        Player survivor = gc.getPlayers().get(0);

        // Call notifyGameOver with the surviving player
        gc.notifyGameOver(survivor);

        // Post-condition 1: exactly one onGameOver call
        assert tracker.gameOverCalls.size() == 1 :
                "Expected exactly 1 onGameOver call, got " + tracker.gameOverCalls.size();

        // Post-condition 2: the argument is the surviving player
        assert tracker.gameOverCalls.get(0) == survivor :
                "Expected onGameOver argument to be the surviving player '"
                        + survivor.getName() + "', got "
                        + (tracker.gameOverCalls.get(0) == null ? "null" : tracker.gameOverCalls.get(0).getName());
    }

    /**
     * Validates: Requirements 3.2
     *
     * Scenario 2: Human eliminated (winner == null).
     * Call notifyGameOver(null), verify the listener receives exactly one
     * onGameOver call with null.
     */
    @Property(tries = 100)
    void gameOverNotificationFiresWithNullWhenHumanEliminated(
            @ForAll @IntRange(min = 3, max = 6) int playerCount,
            @ForAll @IntRange(min = 100, max = 10000) int aiChips) {

        // Build player list
        List<Player> playerList = new ArrayList<>();
        playerList.add(new HumanPlayer("Human", 1000));
        for (int i = 1; i < playerCount; i++) {
            playerList.add(new TestPlayer("AI_" + i, aiChips));
        }

        TrackingListener tracker = new TrackingListener();
        GameController gc = new GameController();
        gc.setGameEventListener(tracker);
        gc.startGame(playerList);

        // Call notifyGameOver with null (human eliminated)
        gc.notifyGameOver(null);

        // Post-condition 1: exactly one onGameOver call
        assert tracker.gameOverCalls.size() == 1 :
                "Expected exactly 1 onGameOver call, got " + tracker.gameOverCalls.size();

        // Post-condition 2: the argument is null
        assert tracker.gameOverCalls.get(0) == null :
                "Expected onGameOver argument to be null, got "
                        + tracker.gameOverCalls.get(0).getName();
    }
}
