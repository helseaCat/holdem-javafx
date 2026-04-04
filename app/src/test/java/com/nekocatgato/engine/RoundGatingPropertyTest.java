package com.nekocatgato.engine;

// Feature: multi-round-game, Property 1: Round gating — engine blocks until signaled

import com.nekocatgato.model.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Property 1: Round gating — engine blocks until signaled
 *
 * For any game state where a round has completed and more than one player remains,
 * the GameController shall not begin the next round until signalNextRound() is called.
 * Specifically, after onRoundComplete fires, the engine thread must be blocked on the
 * nextRoundFuture, and nextRound() must not have been invoked.
 *
 * Validates: Requirements 1.1
 */
class RoundGatingPropertyTest {

    // ── Helper: A HumanPlayer that auto-submits CALL/CHECK so the hand completes ──

    static class AutoHumanPlayer extends HumanPlayer {
        private final Queue<Player.Action> actions;

        AutoHumanPlayer(String name, int chips, List<Player.Action> actionList) {
            super(name, chips);
            this.actions = new LinkedList<>(actionList);
        }

        @Override
        public CompletableFuture<ActionResult> decideActionAsync(GameState state, int callAmount) {
            CompletableFuture<ActionResult> future = super.decideActionAsync(state, callAmount);
            Player.Action action = actions.isEmpty() ? Player.Action.CALL : actions.poll();
            submitAction(action, 0);
            return future;
        }
    }

    // ── Helper: A listener that tracks round completion and game-over ──

    static class GatingListener implements GameEventListener {
        final CountDownLatch roundCompleteLatch = new CountDownLatch(1);
        final AtomicBoolean roundCompleted = new AtomicBoolean(false);
        final AtomicBoolean gameOverFired = new AtomicBoolean(false);
        final AtomicReference<GameState.Phase> lastPhase = new AtomicReference<>();

        @Override
        public void onPhaseChanged(GameState.Phase phase, GameState state) {
            lastPhase.set(phase);
        }

        @Override
        public void onPlayerTurn(Player player, int callAmount) {}

        @Override
        public void onPlayerActed(Player player, Player.Action action) {}

        @Override
        public void onRoundComplete(GameState state) {
            roundCompleted.set(true);
            roundCompleteLatch.countDown();
        }

        @Override
        public void onPlayerEliminated(Player player) {}

        @Override
        public void onGameOver(Player winner) {
            gameOverFired.set(true);
        }

        @Override
        public void onError(Exception e) {}
    }

    // ── Helper: ScriptedPlayer (AI replacement that always CALLs/CHECKs) ──

    static class ScriptedAI extends Player {
        ScriptedAI(String name, int chips) {
            super(name, chips);
        }

        @Override
        public Action decideAction(GameState state, int callAmount) {
            return callAmount == 0 ? Action.CHECK : Action.CALL;
        }
    }

    /**
     * Validates: Requirements 1.1
     *
     * Generate random player counts (2-6), run one hand, assert nextRound not called before signal.
     *
     * Strategy:
     * 1. Start a game with N players (1 human + N-1 AI) on the engine thread
     * 2. Wait for onRoundComplete to fire (the first hand finishes)
     * 3. Assert that nextRoundFuture is non-null and not done (engine is blocked)
     * 4. Assert the game phase has NOT reset to PRE_FLOP (nextRound() not called yet)
     * 5. Signal the next round and verify the engine proceeds
     */
    @Property(tries = 100)
    void engineBlocksAfterRoundCompleteUntilSignaled(
            @ForAll @IntRange(min = 2, max = 6) int playerCount,
            @ForAll @IntRange(min = 5000, max = 50000) int chipAmount) throws Exception {

        // Build player list: 1 AutoHuman + (playerCount-1) ScriptedAI
        // Give enough chips so nobody gets eliminated in one hand
        int chips = chipAmount;
        List<Player.Action> humanActions = new ArrayList<>(Collections.nCopies(10, Player.Action.CALL));
        humanActions.set(0, Player.Action.CALL); // pre-flop
        AutoHumanPlayer human = new AutoHumanPlayer("Human", chips, humanActions);

        List<Player> playerList = new ArrayList<>();
        playerList.add(human);
        for (int i = 1; i < playerCount; i++) {
            playerList.add(new ScriptedAI("AI" + i, chips));
        }

        GameController gc = new GameController();
        GatingListener listener = new GatingListener();
        gc.setGameEventListener(listener);

        // Start the game on the engine executor (async)
        gc.startGameAsync(playerList);

        // Wait for the first round to complete (onRoundComplete fires)
        boolean roundDone = listener.roundCompleteLatch.await(5, TimeUnit.SECONDS);
        assert roundDone : "onRoundComplete should have fired within 5 seconds";
        assert listener.roundCompleted.get() : "roundCompleted flag should be true";

        // Give the engine thread a moment to create the future and block on it
        Thread.sleep(200);

        // If game ended (e.g., someone got eliminated), skip the gating assertion
        if (listener.gameOverFired.get()) {
            return; // game over — no gating expected
        }

        // CORE ASSERTION: nextRoundFuture must exist and NOT be done
        CompletableFuture<Void> future = gc.getNextRoundFuture();
        assert future != null : "nextRoundFuture should be non-null after onRoundComplete";
        assert !future.isDone() : "nextRoundFuture should NOT be done — engine must be blocked";

        // The phase after the hand should be SHOWDOWN (or whatever the last phase was),
        // NOT PRE_FLOP — because nextRound() hasn't been called yet
        GameState.Phase currentPhase = gc.getState().getPhase();
        assert currentPhase != GameState.Phase.PRE_FLOP :
                "Phase should NOT be PRE_FLOP before signalNextRound — engine should be blocked. Got: " + currentPhase;

        // Now signal the next round
        gc.signalNextRound();

        // Give the engine thread time to process
        Thread.sleep(500);

        // After signaling, the future should be done
        assert future.isDone() : "nextRoundFuture should be done after signalNextRound()";

        // After signaling, the engine should have unblocked and proceeded
        assert future.isDone() : "Engine should have unblocked after signal";

        // Clean up: signal again in case the engine is waiting on a second round
        gc.signalNextRound();
    }
}
