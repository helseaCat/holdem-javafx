package com.nekocatgato.engine;

import com.nekocatgato.model.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug condition exploration test for AI check visibility.
 *
 * Validates: Requirements 1.1, 1.2, 1.3
 *
 * Uses behavior-based testing: tracks the sequence of engine events
 * (onPlayerActed, onPhaseChanged) and asserts that a post-round delay
 * occurs between the last AI action and the next phase change.
 *
 * On unfixed code, runSingleHand calls notifyPhaseChanged immediately
 * after runBettingRoundAsync returns — no post-round delay exists.
 * The test detects this by checking that lastRoundActor is an AI
 * and that postRoundDelay() was called before the phase transition.
 *
 * EXPECTED: This test FAILS on unfixed code — failure confirms the bug exists.
 */
class AiCheckVisibilityBugTest {

    private static final int CHIPS = 100_000;

    // ── Helpers ──

    /** HumanPlayer subclass that immediately completes decideActionAsync with CHECK/CALL. */
    static class AutoCheckHuman extends HumanPlayer {
        AutoCheckHuman(String name, int chips) {
            super(name, chips);
        }

        @Override
        public CompletableFuture<ActionResult> decideActionAsync(GameState state, int callAmount) {
            CompletableFuture<ActionResult> future = super.decideActionAsync(state, callAmount);
            Player.Action action = callAmount == 0 ? Player.Action.CHECK : Player.Action.CALL;
            submitAction(action, 0);
            return future;
        }
    }

    /** AI player that always checks (or calls if forced). */
    static class AlwaysCheckAI extends AIPlayer {
        AlwaysCheckAI(String name, int chips) {
            super(name, chips);
        }

        @Override
        public Action decideAction(GameState state, int callAmount) {
            return callAmount == 0 ? Action.CHECK : Action.CALL;
        }
    }

    /**
     * Listener that tracks event ordering to detect whether a post-round
     * delay should have occurred between the last AI action and the phase change.
     *
     * For each phase transition, records whether the last actor before that
     * phase change was an AI and whether there were multiple active players
     * (i.e., the round didn't end via fold-out).
     */
    static class EventOrderListener implements GameEventListener {
        Player lastActedPlayer;
        Player.Action lastAction;
        /** Phases where the last actor before the transition was an AI. */
        final List<GameState.Phase> phasesNeedingPostRoundDelay = new ArrayList<>();

        @Override
        public void onPlayerActed(Player player, Player.Action action, int wagerAmount) {
            lastActedPlayer = player;
            lastAction = action;
        }

        @Override
        public void onPhaseChanged(GameState.Phase phase, GameState state) {
            // Skip PRE_FLOP — no betting round precedes it
            if (phase == GameState.Phase.PRE_FLOP) return;

            if (lastActedPlayer instanceof AIPlayer) {
                phasesNeedingPostRoundDelay.add(phase);
            }
        }

        @Override public void onPlayerTurn(Player player, int callAmount) {}
        @Override public void onRoundComplete(GameState state) {}
        @Override public void onPlayerEliminated(Player player) {}
        @Override public void onGameOver(Player winner) {}
        @Override public void onError(Exception e) {}
    }

    // ── Test Cases ──

    /**
     * 3 players (1 human, 2 AI): all check through all rounds.
     * The last actor in each post-flop betting round is an AI.
     * Asserts that the controller's lastRoundActor is tracked as an AI
     * AND that a post-round delay mechanism exists (which it doesn't on unfixed code).
     */
    @Test
    void threePlayersAllCheck_lastRoundActorIsAiAndPostRoundDelayExists() {
        AlwaysCheckAI ai1 = new AlwaysCheckAI("AI1", CHIPS);
        AutoCheckHuman human = new AutoCheckHuman("Human", CHIPS);
        AlwaysCheckAI ai2 = new AlwaysCheckAI("AI2", CHIPS);

        List<Player> players = List.of(ai1, human, ai2);

        GameController gc = new GameController();
        gc.setAiActionDelay(0, 0); // No delays — instant test
        EventOrderListener listener = new EventOrderListener();
        gc.setGameEventListener(listener);

        gc.startGame(players);
        gc.runSingleHand();

        // Verify the last actor tracking works
        assertNotNull(gc.getLastRoundActor(), "lastRoundActor should be tracked");
        assertInstanceOf(AIPlayer.class, gc.getLastRoundActor(),
                "Last actor in the final betting round should be an AI");

        // Verify that phases needing a post-round delay were detected
        assertFalse(listener.phasesNeedingPostRoundDelay.isEmpty(),
                "At least one phase transition should have an AI as the last actor");

        // THE BUG CHECK: Assert that for each phase where the last actor was an AI,
        // a post-round delay was applied. On unfixed code, runSingleHand does NOT
        // call any post-round delay, so this assertion fails.
        // We check this by verifying the controller has a postRoundDelay mechanism
        // that was invoked. Since no such mechanism exists on unfixed code, we
        // use a subclass approach.
        PostRoundDelayTracker tracker = new PostRoundDelayTracker();
        tracker.setAiActionDelay(0, 0);
        tracker.setGameEventListener(new EventOrderListener());
        tracker.startGame(players);
        tracker.runSingleHand();

        // On unfixed code, postRoundDelayCalled is 0 because runSingleHand
        // never calls postRoundDelay(). On fixed code, it will be > 0.
        assertTrue(tracker.postRoundDelayCalled > 0,
                "postRoundDelay() should be called at least once when last actor is AI "
                        + "and round didn't end via fold-out — but it was called "
                        + tracker.postRoundDelayCalled + " times (bug: no post-round delay exists)");
    }

    /**
     * 4 players (1 human, 3 AI): all check through all rounds.
     * Verifies post-round delay is called for multiple phase transitions.
     */
    @Test
    void fourPlayersAllCheck_postRoundDelayCalledForEachAiLastPhase() {
        AlwaysCheckAI ai1 = new AlwaysCheckAI("AI1", CHIPS);
        AutoCheckHuman human = new AutoCheckHuman("Human", CHIPS);
        AlwaysCheckAI ai2 = new AlwaysCheckAI("AI2", CHIPS);
        AlwaysCheckAI ai3 = new AlwaysCheckAI("AI3", CHIPS);

        List<Player> players = List.of(ai1, human, ai2, ai3);

        PostRoundDelayTracker tracker = new PostRoundDelayTracker();
        tracker.setAiActionDelay(0, 0);
        tracker.setGameEventListener(new EventOrderListener());
        tracker.startGame(players);
        tracker.runSingleHand();

        // With 4 players (1 human, 3 AI) and all checking, the last actor
        // in each post-flop round is an AI. There are 4 betting rounds
        // (pre-flop, flop, turn, river), so we expect post-round delay
        // to be called for at least the flop, turn, and river rounds.
        assertTrue(tracker.postRoundDelayCalled >= 3,
                "postRoundDelay() should be called for flop, turn, and river rounds "
                        + "when last actor is AI — but was called "
                        + tracker.postRoundDelayCalled + " times");
    }

    /**
     * Fold-out scenario: AI folds immediately, leaving fewer players.
     * Post-round delay should NOT be called when round ends via fold-out.
     */
    @Test
    void foldOutRound_postRoundDelayNotCalled() {
        // AI that always folds
        AIPlayer foldAi = new AIPlayer("FoldAI", CHIPS) {
            @Override
            public Action decideAction(GameState state, int callAmount) {
                return Action.FOLD;
            }
        };
        AutoCheckHuman human = new AutoCheckHuman("Human", CHIPS);

        List<Player> players = List.of(foldAi, human);

        PostRoundDelayTracker tracker = new PostRoundDelayTracker();
        tracker.setAiActionDelay(0, 0);
        tracker.setGameEventListener(new EventOrderListener());
        tracker.startGame(players);
        tracker.runSingleHand();

        // When the round ends via fold-out, no post-round delay should occur
        assertEquals(0, tracker.postRoundDelayCalled,
                "postRoundDelay() should NOT be called when round ends via fold-out");
    }

    // ── Tracker subclass ──

    /**
     * GameController subclass that tracks calls to postRoundDelay().
     * On unfixed code, postRoundDelay() is never called from runSingleHand.
     * On fixed code, it will be called after each betting round where the
     * last actor was an AI and the round didn't end via fold-out.
     */
    static class PostRoundDelayTracker extends GameController {
        int postRoundDelayCalled = 0;

        /**
         * Hook point for post-round delay. On unfixed code, this method
         * exists but is never called from runSingleHand.
         */
        protected void postRoundDelay() {
            postRoundDelayCalled++;
        }
    }
}
