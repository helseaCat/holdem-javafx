package com.nekocatgato.engine;

// Feature: multi-round-game, Property 5: Elimination notification fires for each eliminated player

import com.nekocatgato.model.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;

/**
 * Property 5: Elimination notification fires for each eliminated player
 *
 * For any round where K players are eliminated (reach 0 chips), the
 * GameEventListener shall receive exactly K calls to onPlayerEliminated(),
 * one for each eliminated player, and the set of players passed to those
 * calls shall exactly match the set of players who had 0 chips.
 *
 * Validates: Requirements 2.2
 */
class EliminationNotificationPropertyTest {

    /** Tracking listener that records which players were passed to onPlayerEliminated(). */
    static class TrackingListener implements GameEventListener {
        final List<Player> eliminatedPlayers = new ArrayList<>();

        @Override public void onPhaseChanged(GameState.Phase phase, GameState state) {}
        @Override public void onPlayerTurn(Player player, int callAmount) {}
        @Override public void onPlayerActed(Player player, Player.Action action, int wagerAmount) {}
        @Override public void onRoundComplete(GameState state) {}
        @Override public void onGameOver(Player winner) {}
        @Override public void onError(Exception e) {}

        @Override
        public void onPlayerEliminated(Player player) {
            eliminatedPlayers.add(player);
        }
    }

    /** Simple concrete Player for testing — always calls/checks. */
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
     * Validates: Requirements 2.2
     *
     * Generate random chip distributions (some 0), set up a tracking listener,
     * call eliminateBrokePlayers(), verify:
     * - Exactly K calls to onPlayerEliminated (where K = number of 0-chip players)
     * - The set of players in those calls matches the set with 0 chips
     */
    @Property(tries = 100)
    void eliminationNotificationFiresForEachEliminatedPlayer(
            @ForAll @IntRange(min = 3, max = 8) int playerCount,
            @ForAll @IntRange(min = 1000, max = 50000) int baseChips,
            @ForAll Random random) {

        // Build player list with HumanPlayer at index 0 (required by startGame)
        List<Player> playerList = new ArrayList<>();
        playerList.add(new HumanPlayer("Human", baseChips));
        for (int i = 1; i < playerCount; i++) {
            playerList.add(new TestPlayer("P" + i, baseChips));
        }

        TrackingListener tracker = new TrackingListener();

        GameController gc = new GameController();
        gc.setGameEventListener(tracker);
        gc.startGame(playerList);

        List<Player> players = gc.getPlayers();

        // Randomly assign 0 chips to some AI players (keep human alive)
        Set<Player> expectedEliminated = new HashSet<>();

        for (int i = 1; i < players.size(); i++) {
            if (random.nextBoolean()) {
                players.get(i).setChips(0);
                expectedEliminated.add(players.get(i));
            }
        }

        // Need at least 2 survivors for a valid test scenario
        int survivorCount = players.size() - expectedEliminated.size();
        if (survivorCount < 2) return;

        int expectedK = expectedEliminated.size();

        // Execute
        gc.eliminateBrokePlayers();

        // Verify: exactly K calls to onPlayerEliminated
        assert tracker.eliminatedPlayers.size() == expectedK :
                "Expected " + expectedK + " elimination notifications but got "
                        + tracker.eliminatedPlayers.size();

        // Verify: the set of notified players matches the set with 0 chips
        Set<Player> notifiedSet = new HashSet<>(tracker.eliminatedPlayers);
        assert notifiedSet.equals(expectedEliminated) :
                "Notified players do not match expected eliminated players";
    }
}
