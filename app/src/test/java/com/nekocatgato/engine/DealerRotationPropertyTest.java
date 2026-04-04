package com.nekocatgato.engine;

// Feature: multi-round-game, Property 2: Dealer rotation advances by one per round

import com.nekocatgato.model.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Property 2: Dealer rotation advances by one per round
 *
 * For any sequence of N rounds with a fixed set of P players (no eliminations),
 * the dealer button index after round N shall equal (initialDealerIndex + N) % P.
 * The dealer position must advance by exactly one seat per round.
 *
 * Validates: Requirements 1.2
 */
class DealerRotationPropertyTest {

    // ── Helper: A HumanPlayer that auto-submits CALL so the hand completes ──

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

    // ── Helper: AI that always calls/checks — never folds, never raises ──

    static class ScriptedAI extends Player {
        ScriptedAI(String name, int chips) {
            super(name, chips);
        }

        @Override
        public Action decideAction(GameState state, int callAmount) {
            return callAmount == 0 ? Action.CHECK : Action.CALL;
        }
    }

    // ── Helper: Listener that tracks round completions and game-over ──

    static class RotationListener implements GameEventListener {
        final AtomicInteger roundsCompleted = new AtomicInteger(0);
        final AtomicBoolean gameOverFired = new AtomicBoolean(false);
        volatile CountDownLatch roundLatch;

        RotationListener() {
            roundLatch = new CountDownLatch(1);
        }

        void resetLatch() {
            roundLatch = new CountDownLatch(1);
        }

        @Override
        public void onPhaseChanged(GameState.Phase phase, GameState state) {}

        @Override
        public void onPlayerTurn(Player player, int callAmount) {}

        @Override
        public void onPlayerActed(Player player, Player.Action action) {}

        @Override
        public void onRoundComplete(GameState state) {
            roundsCompleted.incrementAndGet();
            roundLatch.countDown();
        }

        @Override
        public void onPlayerEliminated(Player player) {}

        @Override
        public void onGameOver(Player winner) {
            gameOverFired.set(true);
            roundLatch.countDown(); // unblock in case we're waiting
        }

        @Override
        public void onError(Exception e) {}
    }

    /**
     * Validates: Requirements 1.2
     *
     * Generate random player counts (2-6) and round counts (2-5),
     * verify dealerIndex == (initial + rounds) % playerCount after each round.
     */
    @Property(tries = 100)
    void dealerRotatesByOnePerRound(
            @ForAll @IntRange(min = 2, max = 6) int playerCount,
            @ForAll @IntRange(min = 2, max = 5) int totalRounds) throws Exception {

        // High chips so nobody gets eliminated across multiple rounds
        int chips = 100_000;

        AutoHumanPlayer human = new AutoHumanPlayer("Human", chips);
        List<Player> playerList = new ArrayList<>();
        playerList.add(human);
        for (int i = 1; i < playerCount; i++) {
            playerList.add(new ScriptedAI("AI" + i, chips));
        }

        GameController gc = new GameController();
        RotationListener listener = new RotationListener();
        gc.setGameEventListener(listener);

        // Start the game async — first round begins immediately
        gc.startGameAsync(playerList);

        // After startGame + nextRound(), dealerButtonIndex is (−1 + 1) % P = 0
        // That's the initial dealer index for round 1.
        // After round 1 completes and nextRound() is called, it becomes (0 + 1) % P = 1, etc.

        for (int round = 1; round <= totalRounds; round++) {
            // Wait for the round to complete
            boolean done = listener.roundLatch.await(10, TimeUnit.SECONDS);

            if (listener.gameOverFired.get()) {
                // Game ended unexpectedly (e.g., someone lost all chips) — skip
                return;
            }

            assert done : "Round " + round + " should complete within 10 seconds";

            // Give engine thread a moment to create the future and block
            Thread.sleep(100);

            // After round N completes, the dealer index should still be from this round.
            // startGame sets dealerButtonIndex = -1, then nextRound() does (+1) % P = 0 for round 1.
            // So after round N, dealer index = (N - 1) % playerCount
            // (because the first nextRound call sets it to 0, second to 1, etc.)
            int expectedDealer = (round - 1) % playerCount;
            int actualDealer = gc.getDealerButtonIndex();

            assert actualDealer == expectedDealer :
                    "After round " + round + " with " + playerCount + " players, " +
                    "expected dealer index " + expectedDealer + " but got " + actualDealer;

            // Prepare for next round
            if (round < totalRounds) {
                listener.resetLatch();
                gc.signalNextRound();

                // Wait briefly for nextRound() to execute and advance the dealer
                Thread.sleep(200);
            }
        }

        // Final cleanup: signal to unblock the engine if it's waiting
        gc.signalNextRound();
    }
}
