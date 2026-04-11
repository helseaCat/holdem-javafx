package com.nekocatgato.engine;

// Bugfix: clockwise-dealer-rotation, Property 1: Dealer rotates clockwise

import com.nekocatgato.model.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;

/**
 * Property 1: Dealer rotates in clockwise seating order
 *
 * For a 4-player game where the player list is ordered clockwise
 * (You, Bob, Alice, Charlie), the dealer button SHALL advance through
 * indices 0 → 1 → 2 → 3 → 0, which corresponds to the clockwise
 * seating order (bottom → left → top → right).
 *
 * Validates: Requirements 2.1, 2.3
 */
class ClockwiseDealerRotationPropertyTest {

    static class StubPlayer extends Player {
        StubPlayer(String name, int chips) {
            super(name, chips);
        }

        @Override
        public Action decideAction(GameState state, int callAmount) {
            return callAmount == 0 ? Action.CHECK : Action.CALL;
        }
    }

    static class NoOpListener implements GameEventListener {
        @Override public void onPhaseChanged(GameState.Phase phase, GameState state) {}
        @Override public void onPlayerTurn(Player player, int callAmount) {}
        @Override public void onPlayerActed(Player player, Player.Action action, int wagerAmount) {}
        @Override public void onRoundComplete(GameState state) {}
        @Override public void onPlayerEliminated(Player player) {}
        @Override public void onGameOver(Player winner) {}
        @Override public void onError(Exception e) {}
    }

    /**
     * Verify that for any starting dealer position in a 4-player game,
     * advancing the dealer produces the next player in list order,
     * which (with the fix) IS the clockwise seating order.
     */
    @Property(tries = 50)
    void dealerAdvancesClockwiseForFourPlayers(
            @ForAll @IntRange(min = 0, max = 3) int startingDealer) throws Exception {

        int chips = 100_000;
        List<Player> players = List.of(
            new StubPlayer("You", chips),
            new StubPlayer("Bob", chips),
            new StubPlayer("Alice", chips),
            new StubPlayer("Charlie", chips)
        );

        // Expected clockwise order: You(0) → Bob(1) → Alice(2) → Charlie(3)
        String[] expectedOrder = {"You", "Bob", "Alice", "Charlie"};

        GameController gc = new GameController();
        gc.setGameEventListener(new NoOpListener());
        gc.startGame(players);

        // Advance dealer to the starting position
        // startGame calls nextRound which sets dealer to 0
        // We need to advance (startingDealer) more times
        for (int i = 0; i < startingDealer; i++) {
            gc.nextRound();
        }

        assert gc.getDealerButtonIndex() == startingDealer :
                "Setup: dealer should be at " + startingDealer;

        // Advance one more round and verify next dealer is clockwise
        gc.nextRound();
        int nextDealer = gc.getDealerButtonIndex();
        int expectedNext = (startingDealer + 1) % 4;

        assert nextDealer == expectedNext :
                "From dealer " + expectedOrder[startingDealer] + " (index " + startingDealer +
                "), expected next dealer " + expectedOrder[expectedNext] + " (index " + expectedNext +
                ") but got index " + nextDealer;
    }

    /**
     * Verify a full cycle of dealer rotation for N players (2-9)
     * always advances sequentially through list indices.
     */
    @Property(tries = 50)
    void fullCycleRotatesClockwise(
            @ForAll @IntRange(min = 2, max = 9) int playerCount) throws Exception {

        int chips = 100_000;
        List<Player> players = new ArrayList<>();
        players.add(new StubPlayer("You", chips));
        for (int i = 1; i < playerCount; i++) {
            players.add(new StubPlayer("AI" + i, chips));
        }

        GameController gc = new GameController();
        gc.setGameEventListener(new NoOpListener());
        gc.startGame(players);

        // After startGame + nextRound, dealer is at 0
        assert gc.getDealerButtonIndex() == 0;

        // Advance through a full cycle
        for (int round = 1; round <= playerCount; round++) {
            gc.nextRound();
            int expected = round % playerCount;
            int actual = gc.getDealerButtonIndex();
            assert actual == expected :
                    "Round " + round + " with " + playerCount + " players: " +
                    "expected dealer " + expected + " but got " + actual;
        }
    }
}
