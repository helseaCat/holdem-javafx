package com.nekocatgato.engine;

// Bugfix: clockwise-dealer-rotation, Property 2: Preservation

import com.nekocatgato.model.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Property 2: Preservation - Blind posting, elimination, heads-up, and reset unchanged
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4
 */
class ClockwiseDealerPreservationPropertyTest {

    static class StubPlayer extends Player {
        StubPlayer(String name, int chips) {
            super(name, chips);
        }

        @Override
        public Action decideAction(GameState state, int callAmount) {
            return callAmount == 0 ? Action.CHECK : Action.CALL;
        }
    }

    static class AutoHumanPlayer extends HumanPlayer {
        AutoHumanPlayer(String name, int chips) {
            super(name, chips);
        }

        @Override
        public CompletableFuture<ActionResult> decideActionAsync(GameState state, int callAmount) {
            CompletableFuture<ActionResult> future = super.decideActionAsync(state, callAmount);
            submitAction(Player.Action.CALL, 0);
            return future;
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

    private static void setField(GameController gc, String fieldName, Object value) throws Exception {
        Field field = GameController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(gc, value);
    }

    /**
     * Property: For all player counts (2-9) and dealer positions,
     * small blind is deducted from (dealer+1) and big blind from (dealer+2).
     */
    @Property(tries = 100)
    void blindsPostedAtCorrectPositions(
            @ForAll @IntRange(min = 2, max = 9) int playerCount,
            @ForAll @IntRange(min = 500, max = 5000) int initialChips) throws Exception {

        List<Player> players = new ArrayList<>();
        players.add(new StubPlayer("You", initialChips));
        for (int i = 1; i < playerCount; i++) {
            players.add(new StubPlayer("AI" + i, initialChips));
        }

        GameController gc = new GameController();
        gc.setGameEventListener(new NoOpListener());
        gc.startGame(players);

        // After startGame, dealer is at 0, blinds posted
        int dealerIdx = gc.getDealerButtonIndex();
        int sbIdx = (dealerIdx + 1) % playerCount;
        int bbIdx = (dealerIdx + 2) % playerCount;

        Player sbPlayer = gc.getPlayers().get(sbIdx);
        Player bbPlayer = gc.getPlayers().get(bbIdx);

        int expectedSB = Math.min(GameController.SMALL_BLIND, initialChips);
        int expectedBB = Math.min(GameController.BIG_BLIND, initialChips);

        assert sbPlayer.getCurrentBet() == expectedSB :
                "SB player at index " + sbIdx + " should have bet " + expectedSB +
                " but has " + sbPlayer.getCurrentBet();

        assert bbPlayer.getCurrentBet() == expectedBB :
                "BB player at index " + bbIdx + " should have bet " + expectedBB +
                " but has " + bbPlayer.getCurrentBet();

        // Total pot should equal SB + BB
        int expectedPot = expectedSB + expectedBB;
        assert gc.getState().getPot() == expectedPot :
                "Pot should be " + expectedPot + " but is " + gc.getState().getPot();
    }

    /**
     * Property: For 2-player games, dealer posts SB and other player posts BB.
     */
    @Property(tries = 50)
    void headsUpBlindsCorrect(
            @ForAll @IntRange(min = 500, max = 5000) int initialChips) throws Exception {

        List<Player> players = List.of(
            new StubPlayer("You", initialChips),
            new StubPlayer("AI1", initialChips)
        );

        GameController gc = new GameController();
        gc.setGameEventListener(new NoOpListener());
        gc.startGame(players);

        // Dealer is at 0, SB at (0+1)%2=1, BB at (0+2)%2=0
        // In heads-up, dealer+1 is the other player (SB), dealer+2 wraps to dealer (BB)
        int dealerIdx = gc.getDealerButtonIndex();
        int sbIdx = (dealerIdx + 1) % 2;
        int bbIdx = (dealerIdx + 2) % 2;

        Player sbPlayer = gc.getPlayers().get(sbIdx);
        Player bbPlayer = gc.getPlayers().get(bbIdx);

        int expectedSB = Math.min(GameController.SMALL_BLIND, initialChips);
        int expectedBB = Math.min(GameController.BIG_BLIND, initialChips);

        assert sbPlayer.getCurrentBet() == expectedSB :
                "Heads-up SB should be " + expectedSB + " but is " + sbPlayer.getCurrentBet();
        assert bbPlayer.getCurrentBet() == expectedBB :
                "Heads-up BB should be " + expectedBB + " but is " + bbPlayer.getCurrentBet();
    }

    /**
     * Property: After elimination, dealer index is valid in the shortened list.
     */
    @Property(tries = 100)
    void eliminationPreservesDealerValidity(
            @ForAll @IntRange(min = 3, max = 9) int playerCount,
            @ForAll @IntRange(min = 0, max = 7) int eliminateIndex,
            @ForAll Random random) throws Exception {

        if (eliminateIndex >= playerCount) return; // skip invalid combos

        int chips = 1000;
        List<Player> players = new ArrayList<>();
        players.add(new StubPlayer("You", chips));
        for (int i = 1; i < playerCount; i++) {
            players.add(new StubPlayer("AI" + i, chips));
        }

        GameController gc = new GameController();
        gc.setGameEventListener(new NoOpListener());
        gc.startGame(players);

        // Set a random dealer position
        int dealerPos = random.nextInt(playerCount);
        setField(gc, "dealerButtonIndex", dealerPos);

        // Eliminate one player by zeroing their chips
        gc.getPlayers().get(eliminateIndex).setChips(0);
        List<Integer> eliminated = gc.eliminateBrokePlayers();
        gc.adjustDealerIndexAfterElimination(eliminated);

        int newDealerIdx = gc.getDealerButtonIndex();
        int newSize = gc.getPlayers().size();

        // Dealer index must be valid: either -1 (will be fixed by nextRound) or in [0, newSize)
        assert newDealerIdx >= -1 && newDealerIdx < newSize :
                "After eliminating index " + eliminateIndex + " from " + playerCount +
                " players (dealer was " + dealerPos + "), dealer index " + newDealerIdx +
                " is out of range [−1, " + (newSize - 1) + "]";
    }

    /**
     * Property: resetAndRestart restores all players with initial chips
     * and resets dealer to 0 (after nextRound inside reset).
     */
    @Property(tries = 50)
    void resetRestoresInitialState(
            @ForAll @IntRange(min = 2, max = 6) int playerCount,
            @ForAll @IntRange(min = 500, max = 5000) int initialChips) throws Exception {

        AutoHumanPlayer human = new AutoHumanPlayer("Human", initialChips);
        List<Player> players = new ArrayList<>();
        players.add(human);
        for (int i = 1; i < playerCount; i++) {
            players.add(new StubPlayer("AI" + i, initialChips));
        }

        GameController gc = new GameController();
        gc.setGameEventListener(new NoOpListener());
        gc.startGame(players);

        // Simulate game over
        setField(gc, "gameOver", true);
        setField(gc, "gameWinner", gc.getPlayers().get(0));

        gc.resetAndRestart();

        // All players restored
        assert gc.getPlayers().size() == playerCount :
                "Expected " + playerCount + " players after reset, got " + gc.getPlayers().size();

        // Game not over
        assert !gc.isGameOver() : "Game should not be over after reset";
        assert gc.getGameWinner() == null : "Winner should be null after reset";

        // Dealer at 0 (reset sets to -1, nextRound advances to 0)
        assert gc.getDealerButtonIndex() == 0 :
                "Dealer should be 0 after reset, got " + gc.getDealerButtonIndex();

        // Total chips preserved (accounting for blinds in pot)
        int totalChips = 0;
        for (Player p : gc.getPlayers()) {
            totalChips += p.getChips();
        }
        totalChips += gc.getState().getPot();
        assert totalChips == playerCount * initialChips :
                "Total chips should be " + (playerCount * initialChips) + " but got " + totalChips;

        // Clean up async executor
        gc.signalNextRound();
    }
}
