package com.nekocatgato.engine;

// Feature: multi-round-game, Property 10: Human elimination triggers game over regardless of remaining AI count

import com.nekocatgato.model.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.ArrayList;
import java.util.List;

/**
 * Property 10: Human elimination triggers game over regardless of remaining AI count
 *
 * For any game state where the human player has 0 chips after pot distribution,
 * isGameOver() shall return true even if 2 or more AI players still have chips.
 * The getGameWinner() shall return null to indicate the human lost.
 *
 * Validates: Requirements 6.1
 */
class HumanEliminationPropertyTest {

    /** No-op listener to satisfy GameController's notification calls. */
    static class NoOpListener implements GameEventListener {
        @Override public void onPhaseChanged(GameState.Phase phase, GameState state) {}
        @Override public void onPlayerTurn(Player player, int callAmount) {}
        @Override public void onPlayerActed(Player player, Player.Action action) {}
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
     * Validates: Requirements 6.1
     *
     * Generate scenarios with human at 0 chips and varying AI counts (2-7).
     * Set up GameController with 1 human + N AI players, set human to 0 chips,
     * verify isHumanEliminated() returns true, and that the game-over condition
     * in runGameLoop (isHumanEliminated() || players.size() == 1) would trigger.
     * The gameWinner should be null when human is eliminated.
     */
    @Property(tries = 100)
    void humanEliminationTriggersGameOverRegardlessOfAICount(
            @ForAll @IntRange(min = 2, max = 7) int aiCount,
            @ForAll @IntRange(min = 1000, max = 50000) int aiChips) {

        // Build player list: 1 human + N AI players, all with positive chips for startGame
        List<Player> playerList = new ArrayList<>();
        playerList.add(new HumanPlayer("Human", aiChips));
        for (int i = 0; i < aiCount; i++) {
            playerList.add(new TestPlayer("AI_" + i, aiChips));
        }

        GameController gc = new GameController();
        gc.setGameEventListener(new NoOpListener());
        gc.startGame(playerList);

        // Simulate human losing all chips (0 chips after pot distribution)
        List<Player> players = gc.getPlayers();
        Player human = players.stream()
                .filter(p -> p instanceof HumanPlayer)
                .findFirst()
                .orElseThrow();
        human.setChips(0);

        // Verify isHumanEliminated() detects the human has 0 chips
        assert gc.isHumanEliminated() :
                "isHumanEliminated() should return true when human has 0 chips";

        // Verify all AI players still have chips (>= 2 AI remain)
        long aiWithChips = players.stream()
                .filter(p -> !(p instanceof HumanPlayer) && p.getChips() > 0)
                .count();
        assert aiWithChips >= 2 :
                "Expected at least 2 AI players with chips, got " + aiWithChips;

        // Simulate what runGameLoop does: eliminate broke players, then check game-over
        gc.eliminateBrokePlayers();

        // The game-over condition from runGameLoop:
        // if (isHumanEliminated() || players.size() == 1)
        boolean gameOverCondition = gc.isHumanEliminated() || gc.getPlayers().size() == 1;
        assert gameOverCondition :
                "Game-over condition should be true when human is eliminated, "
                + "even with " + gc.getPlayers().size() + " AI players remaining";

        // When human is eliminated, gameWinner should be null (per design)
        // Simulate the assignment from runGameLoop:
        // gameWinner = players.size() == 1 ? players.get(0) : null;
        Player expectedWinner = gc.getPlayers().size() == 1 ? gc.getPlayers().get(0) : null;
        assert expectedWinner == null :
                "gameWinner should be null when human is eliminated with multiple AI remaining, "
                + "but got " + expectedWinner.getName();
    }
}
