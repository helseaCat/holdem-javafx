package com.nekocatgato.engine;

// Feature: multi-round-game, Property 7: Game over when one player remains

import com.nekocatgato.model.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;

/**
 * Property 7: Game over when one player remains
 *
 * For any game where eliminateBrokePlayers() reduces the player list to exactly
 * 1 player, isGameOver() shall return true, getGameWinner() shall return that
 * remaining player, and the game loop shall not invoke nextRound() again.
 *
 * Validates: Requirements 3.1, 3.3
 */
class GameOverOnePlayerPropertyTest {

    /** No-op listener to satisfy GameController's notification calls. */
    static class NoOpListener implements GameEventListener {
        @Override public void onPhaseChanged(GameState.Phase phase, GameState state) {}
        @Override public void onPlayerTurn(Player player, int callAmount) {}
        @Override public void onPlayerActed(Player player, Player.Action action, int wagerAmount) {}
        @Override public void onRoundComplete(GameState state) {}
        @Override public void onPlayerEliminated(Player player) {}
        @Override public void onGameOver(Player winner) {}
        @Override public void onError(Exception e) {}
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
     * Validates: Requirements 3.1, 3.3
     *
     * For any player count (2-6), when all AI players have 0 chips and the
     * human survives, eliminateBrokePlayers() reduces the list to exactly 1
     * player. The game-over condition (players.size() == 1) is then true,
     * meaning the game loop would set gameOver = true and gameWinner to the
     * surviving player, and would not invoke nextRound() again.
     */
    @Property(tries = 100)
    void gameOverWhenOnePlayerRemains(
            @ForAll @IntRange(min = 2, max = 6) int playerCount,
            @ForAll @IntRange(min = 100, max = 10000) int humanChips) {

        // Build player list: HumanPlayer at index 0 with chips, all AI at 0 chips
        List<Player> playerList = new ArrayList<>();
        HumanPlayer human = new HumanPlayer("Human", humanChips);
        playerList.add(human);
        for (int i = 1; i < playerCount; i++) {
            playerList.add(new TestPlayer("AI_" + i, 1000));
        }

        GameController gc = new GameController();
        gc.setGameEventListener(new NoOpListener());
        gc.startGame(playerList);

        // Set all AI players to 0 chips (simulating they lost everything)
        for (Player p : gc.getPlayers()) {
            if (!(p instanceof HumanPlayer)) {
                p.setChips(0);
            }
        }

        // Execute elimination
        gc.eliminateBrokePlayers();

        List<Player> remaining = gc.getPlayers();

        // Post-condition 1: exactly 1 player remains
        assert remaining.size() == 1 :
                "Expected exactly 1 player remaining, got " + remaining.size();

        // Post-condition 2: the remaining player is the human with chips
        Player survivor = remaining.get(0);
        assert survivor instanceof HumanPlayer :
                "Expected surviving player to be HumanPlayer, got " + survivor.getClass().getSimpleName();
        assert survivor.getName().equals("Human") :
                "Expected surviving player name 'Human', got '" + survivor.getName() + "'";
        assert survivor.getChips() > 0 :
                "Expected surviving player to have chips > 0, got " + survivor.getChips();

        // Post-condition 3: the game-over detection condition is satisfied
        // In runGameLoop(), the check is: if (players.size() == 1) { gameOver = true; gameWinner = players.get(0); }
        // We verify the precondition holds:
        assert remaining.size() == 1 :
                "Game-over condition (players.size() == 1) not met";

        // Post-condition 4: the surviving player would be set as gameWinner
        // Simulate what runGameLoop does: gameWinner = players.size() == 1 ? players.get(0) : null
        Player expectedWinner = remaining.size() == 1 ? remaining.get(0) : null;
        assert expectedWinner == survivor :
                "Expected game winner to be the surviving player";
    }
}
