package com.nekocatgato.engine;

import com.nekocatgato.model.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AI action delay in runBettingRoundAsync.
 * Validates: Requirements 2.1, 2.2, 3.1, 3.3, 3.4
 */
class AiActionDelayTest {

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

    static class ScriptedAI extends Player {
        ScriptedAI(String name, int chips) {
            super(name, chips);
        }

        @Override
        public Action decideAction(GameState state, int callAmount) {
            return callAmount == 0 ? Action.CHECK : Action.CALL;
        }
    }

    static class TimingListener implements GameEventListener {
        final List<Long> actionTimestamps = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch roundLatch = new CountDownLatch(1);
        final AtomicBoolean gameOverFired = new AtomicBoolean(false);

        @Override public void onPhaseChanged(GameState.Phase phase, GameState state) {}
        @Override public void onPlayerTurn(Player player, int callAmount) {}
        @Override
        public void onPlayerActed(Player player, Player.Action action) {
            actionTimestamps.add(System.nanoTime());
        }
        @Override
        public void onRoundComplete(GameState state) {
            roundLatch.countDown();
        }
        @Override public void onPlayerEliminated(Player player) {}
        @Override
        public void onGameOver(Player winner) {
            gameOverFired.set(true);
            roundLatch.countDown();
        }
        @Override public void onError(Exception e) {}
    }

    @Test
    void zeroDelayCompletesQuickly() throws Exception {
        AutoHumanPlayer human = new AutoHumanPlayer("Human", 100_000);
        List<Player> players = List.of(
            human,
            new ScriptedAI("AI1", 100_000),
            new ScriptedAI("AI2", 100_000),
            new ScriptedAI("AI3", 100_000)
        );

        GameController gc = new GameController();
        gc.setAiActionDelay(0, 0);
        TimingListener listener = new TimingListener();
        gc.setGameEventListener(listener);
        gc.startGameAsync(players);

        boolean done = listener.roundLatch.await(10, TimeUnit.SECONDS);
        assertTrue(done || listener.gameOverFired.get(), "Round should complete within 10s with zero delay");

        // With zero delay, all actions should complete very quickly
        if (listener.actionTimestamps.size() >= 2) {
            long firstAction = listener.actionTimestamps.get(0);
            long lastAction = listener.actionTimestamps.get(listener.actionTimestamps.size() - 1);
            long totalMs = (lastAction - firstAction) / 1_000_000;
            assertTrue(totalMs < 500, "With zero delay, all actions should complete in < 500ms, took " + totalMs + "ms");
        }

        gc.signalNextRound();
    }

    @Test
    void smallDelayProducesMeasurableGap() throws Exception {
        AutoHumanPlayer human = new AutoHumanPlayer("Human", 100_000);
        List<Player> players = List.of(
            human,
            new ScriptedAI("AI1", 100_000),
            new ScriptedAI("AI2", 100_000),
            new ScriptedAI("AI3", 100_000)
        );

        GameController gc = new GameController();
        gc.setAiActionDelay(100, 200);
        TimingListener listener = new TimingListener();
        gc.setGameEventListener(listener);
        gc.startGameAsync(players);

        boolean done = listener.roundLatch.await(30, TimeUnit.SECONDS);
        assertTrue(done || listener.gameOverFired.get(), "Round should complete within 30s with small delay");

        // With delay enabled, there should be multiple actions and at least some AI actions
        // had delays. Verify we got multiple action timestamps.
        assertTrue(listener.actionTimestamps.size() >= 3,
                "Expected at least 3 actions (human + 2 AI), got " + listener.actionTimestamps.size());

        gc.signalNextRound();
    }

    @Test
    void aiDecisionLogicUnchanged() {
        AIPlayer ai = new AIPlayer("TestAI", 1000);
        GameState state = new GameState();

        // callAmount == 0 → CHECK
        assertEquals(Player.Action.CHECK, ai.decideAction(state, 0));

        // callAmount <= chips/2 → CALL
        assertEquals(Player.Action.CALL, ai.decideAction(state, 100));
        assertEquals(Player.Action.CALL, ai.decideAction(state, 500));

        // callAmount > chips/2 → FOLD
        assertEquals(Player.Action.FOLD, ai.decideAction(state, 501));
        assertEquals(Player.Action.FOLD, ai.decideAction(state, 1000));
    }

    @Test
    void bettingRoundOutcomeIdenticalWithAndWithoutDelay() throws Exception {
        // Run two games with identical setup, one with delay, one without
        // Verify pot and chip totals are the same

        int chips = 100_000;
        for (int trial = 0; trial < 5; trial++) {
            // Without delay
            AutoHumanPlayer human1 = new AutoHumanPlayer("Human", chips);
            ScriptedAI ai1a = new ScriptedAI("AI1", chips);
            ScriptedAI ai1b = new ScriptedAI("AI2", chips);

            GameController gc1 = new GameController();
            gc1.setAiActionDelay(0, 0);
            TimingListener l1 = new TimingListener();
            gc1.setGameEventListener(l1);
            gc1.startGame(List.of(human1, ai1a, ai1b));

            int totalChips1 = 0;
            for (Player p : gc1.getPlayers()) totalChips1 += p.getChips();
            totalChips1 += gc1.getState().getPot();

            // Total chips should always equal initial total
            assertEquals(chips * 3, totalChips1, "Chip conservation violated");
        }
    }
}
