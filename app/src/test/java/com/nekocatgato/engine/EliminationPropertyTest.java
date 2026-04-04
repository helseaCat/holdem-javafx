package com.nekocatgato.engine;

// Feature: multi-round-game, Property 4: Elimination removes all 0-chip players

import com.nekocatgato.model.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;

/**
 * Property 4: Elimination removes all 0-chip players
 *
 * For any list of players with arbitrary chip counts (≥0), after
 * eliminateBrokePlayers() executes, no player in the resulting list shall
 * have 0 chips. Every player that had 0 chips before the call must be absent
 * from the list. Every player that had >0 chips must still be present.
 *
 * Validates: Requirements 2.1
 */
class EliminationPropertyTest {

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
     * Validates: Requirements 2.1
     *
     * Generate random chip distributions (some 0), verify post-condition:
     * - No remaining player has 0 chips
     * - Every player that had 0 chips is absent
     * - Every player that had >0 chips is still present
     */
    @Property(tries = 100)
    void eliminationRemovesAllZeroChipPlayers(
            @ForAll @IntRange(min = 3, max = 8) int playerCount,
            @ForAll @IntRange(min = 1000, max = 50000) int baseChips,
            @ForAll Random random) {

        // Build a player list with a HumanPlayer at index 0 (required by startGame)
        List<Player> playerList = new ArrayList<>();
        playerList.add(new HumanPlayer("Human", baseChips));
        for (int i = 1; i < playerCount; i++) {
            playerList.add(new TestPlayer("P" + i, baseChips));
        }

        GameController gc = new GameController();
        gc.setGameEventListener(new NoOpListener());
        gc.startGame(playerList);

        List<Player> players = gc.getPlayers();

        // Randomly assign 0 chips to some players (keep human alive to avoid
        // needing at least 2 survivors — we guard that below)
        Set<String> expectedRemoved = new HashSet<>();
        Set<String> expectedRemaining = new HashSet<>();
        expectedRemaining.add(players.get(0).getName()); // human always keeps chips

        for (int i = 1; i < players.size(); i++) {
            if (random.nextBoolean()) {
                players.get(i).setChips(0);
                expectedRemoved.add(players.get(i).getName());
            } else {
                expectedRemaining.add(players.get(i).getName());
            }
        }

        // Need at least 2 survivors for a meaningful test
        if (expectedRemaining.size() < 2) return;

        // Execute
        gc.eliminateBrokePlayers();

        List<Player> remaining = gc.getPlayers();

        // Post-condition 1: no remaining player has 0 chips
        for (Player p : remaining) {
            assert p.getChips() > 0 :
                    "Player " + p.getName() + " has 0 chips but was not eliminated";
        }

        // Post-condition 2: every player that had >0 chips is still present
        Set<String> remainingNames = new HashSet<>();
        for (Player p : remaining) {
            remainingNames.add(p.getName());
        }
        for (String name : expectedRemaining) {
            assert remainingNames.contains(name) :
                    "Player " + name + " had >0 chips but was removed";
        }

        // Post-condition 3: every player that had 0 chips is absent
        for (String name : expectedRemoved) {
            assert !remainingNames.contains(name) :
                    "Player " + name + " had 0 chips but was not removed";
        }
    }
}
