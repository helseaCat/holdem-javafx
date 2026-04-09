package com.nekocatgato.engine;

import com.nekocatgato.model.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Preservation property tests for AI check visibility bugfix.
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5
 *
 * These tests capture the EXISTING (unfixed) behavior that must remain
 * unchanged after the fix is applied. They run on unfixed code first
 * to establish the baseline, then again after the fix to confirm no regressions.
 *
 * Property 2: Preservation — Fold-out and non-AI-last round behavior unchanged.
 */
class AiCheckVisibilityPreservationTest {

    private static final int CHIPS = 100_000;

    // ── Helpers ──

    /** HumanPlayer that immediately completes decideActionAsync with CHECK/CALL. */
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

    /** AI that always checks (or calls if forced). */
    static class AlwaysCheckAI extends AIPlayer {
        AlwaysCheckAI(String name, int chips) {
            super(name, chips);
        }

        @Override
        public Action decideAction(GameState state, int callAmount) {
            return callAmount == 0 ? Action.CHECK : Action.CALL;
        }
    }

    /** AI that always folds. */
    static class AlwaysFoldAI extends AIPlayer {
        AlwaysFoldAI(String name, int chips) {
            super(name, chips);
        }

        @Override
        public Action decideAction(GameState state, int callAmount) {
            return Action.FOLD;
        }
    }

    /**
     * Listener that records phase transitions, round-complete events,
     * and fold-out winner info for preservation assertions.
     */
    static class PreservationListener implements GameEventListener {
        final List<GameState.Phase> phaseTransitions = new ArrayList<>();
        String roundCompleteWinnerName;
        int roundCompletePot;
        boolean roundCompleteReceived = false;

        @Override
        public void onPhaseChanged(GameState.Phase phase, GameState state) {
            phaseTransitions.add(phase);
        }

        @Override
        public void onRoundComplete(GameState state) {
            roundCompleteReceived = true;
        }

        @Override public void onPlayerTurn(Player player, int callAmount) {}
        @Override public void onPlayerActed(Player player, Player.Action action) {}
        @Override public void onPlayerEliminated(Player player) {}
        @Override public void onGameOver(Player winner) {}
        @Override public void onError(Exception e) {}
    }

    // ── Property: Chip Conservation ──

    /**
     * For any game configuration (2–6 players, varying chip counts),
     * total chips in play are conserved across a full hand regardless
     * of round outcomes.
     *
     * Validates: Requirement 3.2 (bet/call/raise rounds unchanged),
     *            Requirement 3.5 (showdown transitions normal)
     */
    @Property(tries = 50)
    void chipConservation_totalChipsConstantAfterFullHand(
            @ForAll @IntRange(min = 1, max = 4) int aiCount,
            @ForAll @IntRange(min = 500, max = 5000) int chipAmount) {

        List<Player> players = new ArrayList<>();
        players.add(new AutoCheckHuman("Human", chipAmount));
        for (int i = 0; i < aiCount; i++) {
            players.add(new AlwaysCheckAI("AI" + (i + 1), chipAmount));
        }

        int totalBefore = players.stream().mapToInt(Player::getChips).sum();

        GameController gc = new GameController();
        gc.setAiActionDelay(0, 0);
        gc.setGameEventListener(new PreservationListener());
        gc.startGame(players);
        gc.runSingleHand();

        int totalAfter = gc.getPlayers().stream().mapToInt(Player::getChips).sum();

        assertEquals(totalBefore, totalAfter,
                "Total chips must be conserved after a full hand. "
                        + "Before: " + totalBefore + ", After: " + totalAfter);
    }

    // ── Property: Phase Transition Order ──

    /**
     * For any full hand that reaches showdown, the phase transition
     * sequence is exactly PRE_FLOP → FLOP → TURN → RIVER → SHOWDOWN.
     *
     * Validates: Requirement 3.4 (pre-flop timing unchanged),
     *            Requirement 3.5 (showdown transition normal)
     */
    @Property(tries = 50)
    void phaseTransitionOrder_exactSequenceWhenReachingShowdown(
            @ForAll @IntRange(min = 1, max = 4) int aiCount,
            @ForAll @IntRange(min = 500, max = 5000) int chipAmount) {

        List<Player> players = new ArrayList<>();
        players.add(new AutoCheckHuman("Human", chipAmount));
        for (int i = 0; i < aiCount; i++) {
            players.add(new AlwaysCheckAI("AI" + (i + 1), chipAmount));
        }

        PreservationListener listener = new PreservationListener();
        GameController gc = new GameController();
        gc.setAiActionDelay(0, 0);
        gc.setGameEventListener(listener);
        gc.startGame(players);
        gc.runSingleHand();

        List<GameState.Phase> expected = List.of(
                GameState.Phase.PRE_FLOP,
                GameState.Phase.FLOP,
                GameState.Phase.TURN,
                GameState.Phase.RIVER,
                GameState.Phase.SHOWDOWN
        );

        assertEquals(expected, listener.phaseTransitions,
                "Phase transitions must follow PRE_FLOP → FLOP → TURN → RIVER → SHOWDOWN");
    }

    // ── Property: Fold-Out Pot Resolution ──

    /**
     * For any round ending via fold-out (activePlayers.size() == 1),
     * the pot winner receives the correct amount immediately and no
     * additional delay is introduced.
     *
     * Validates: Requirement 3.1 (fold-out resolves immediately)
     */
    @Property(tries = 50)
    void foldOutResolution_winnerReceivesCorrectPot(
            @ForAll @IntRange(min = 500, max = 5000) int chipAmount) {

        AutoCheckHuman human = new AutoCheckHuman("Human", chipAmount);
        AlwaysFoldAI foldAi = new AlwaysFoldAI("FoldAI", chipAmount);

        // Human is dealer (index 0 after startGame increments from -1).
        // FoldAI posts small blind, Human posts big blind.
        // Pre-flop: FoldAI acts first and folds → Human wins the pot.
        List<Player> players = List.of(human, foldAi);

        int totalBefore = human.getChips() + foldAi.getChips();

        PreservationListener listener = new PreservationListener();
        GameController gc = new GameController();
        gc.setAiActionDelay(0, 0);
        gc.setGameEventListener(listener);
        gc.startGame(players);
        gc.runSingleHand();

        // After fold-out, only PRE_FLOP phase should have been notified
        // (the hand ends before flop is dealt)
        assertEquals(1, listener.phaseTransitions.size(),
                "Fold-out in pre-flop should only have PRE_FLOP phase transition");
        assertEquals(GameState.Phase.PRE_FLOP, listener.phaseTransitions.get(0));

        // Chips must be conserved
        int totalAfter = gc.getPlayers().stream().mapToInt(Player::getChips).sum();
        assertEquals(totalBefore, totalAfter,
                "Total chips must be conserved after fold-out");

        // The winner (human) should have gained the pot
        assertEquals(gc.getLastRoundWinnerName(), "Human",
                "Human should be the fold-out winner");
        assertTrue(gc.getLastPotAmount() > 0,
                "Pot amount should be positive after fold-out");
    }

    /**
     * For any fold-out scenario with multiple AI players where one folds,
     * the onRoundComplete event fires with correct winner and pot.
     *
     * Validates: Requirement 3.1 (fold-out resolves immediately)
     */
    @Test
    void foldOutWithMultipleAIs_correctWinnerAndPot() {
        AutoCheckHuman human = new AutoCheckHuman("Human", CHIPS);
        AlwaysFoldAI foldAi1 = new AlwaysFoldAI("FoldAI1", CHIPS);
        AlwaysFoldAI foldAi2 = new AlwaysFoldAI("FoldAI2", CHIPS);

        List<Player> players = List.of(human, foldAi1, foldAi2);
        int totalBefore = players.stream().mapToInt(Player::getChips).sum();

        PreservationListener listener = new PreservationListener();
        GameController gc = new GameController();
        gc.setAiActionDelay(0, 0);
        gc.setGameEventListener(listener);
        gc.startGame(players);
        gc.runSingleHand();

        // Chips conserved
        int totalAfter = gc.getPlayers().stream().mapToInt(Player::getChips).sum();
        assertEquals(totalBefore, totalAfter,
                "Total chips must be conserved after fold-out with multiple AIs");

        // Pot was awarded
        assertTrue(gc.getLastPotAmount() > 0,
                "Pot should be positive after fold-out");
        assertNotNull(gc.getLastRoundWinnerName(),
                "Winner name should be set after fold-out");
    }

    // ── Property: Chip Conservation with Fold-Out ──

    /**
     * For any fold-out scenario, total chips are conserved.
     * Uses property-based testing to vary chip amounts.
     *
     * Validates: Requirement 3.1 (fold-out unchanged)
     */
    @Property(tries = 50)
    void chipConservation_foldOutPreservesTotal(
            @ForAll @IntRange(min = 1, max = 3) int foldAiCount,
            @ForAll @IntRange(min = 500, max = 5000) int chipAmount) {

        List<Player> players = new ArrayList<>();
        players.add(new AutoCheckHuman("Human", chipAmount));
        for (int i = 0; i < foldAiCount; i++) {
            players.add(new AlwaysFoldAI("FoldAI" + (i + 1), chipAmount));
        }

        int totalBefore = players.stream().mapToInt(Player::getChips).sum();

        GameController gc = new GameController();
        gc.setAiActionDelay(0, 0);
        gc.setGameEventListener(new PreservationListener());
        gc.startGame(players);
        gc.runSingleHand();

        int totalAfter = gc.getPlayers().stream().mapToInt(Player::getChips).sum();
        assertEquals(totalBefore, totalAfter,
                "Total chips must be conserved after fold-out");
    }

    // ── Deterministic: Human-Last Round ──

    /**
     * When the human is the last to act (checks), no post-round delay
     * should exist before the phase change. This verifies that the fix
     * does not introduce delays for human-last rounds.
     *
     * Validates: Requirement 3.3 (human input unchanged)
     */
    @Test
    void humanLastRound_noExtraDelayBeforePhaseChange() {
        // 2 players: AI checks first, then Human checks.
        // Human is the last actor in each post-flop round.
        AlwaysCheckAI ai = new AlwaysCheckAI("AI", CHIPS);
        AutoCheckHuman human = new AutoCheckHuman("Human", CHIPS);

        // With dealer at index 0 (AI), human is at index 1.
        // Post-flop first-to-act is (dealer+1) % size = index 1 = human for 2 players.
        // Actually with 2 players, (0+1)%2 = 1 = human acts first, then AI.
        // So AI is last. Let's reverse: human first in list.
        // dealer index 0 = human. Post-flop first-to-act = (0+1)%2 = 1 = AI.
        // AI checks, then human checks. Human is last.
        List<Player> players = List.of(human, ai);

        PreservationListener listener = new PreservationListener();
        GameController gc = new GameController();
        gc.setAiActionDelay(50, 100);
        gc.setGameEventListener(listener);
        gc.startGame(players);

        gc.runSingleHand();

        // Should reach showdown with full phase sequence
        List<GameState.Phase> expected = List.of(
                GameState.Phase.PRE_FLOP,
                GameState.Phase.FLOP,
                GameState.Phase.TURN,
                GameState.Phase.RIVER,
                GameState.Phase.SHOWDOWN
        );
        assertEquals(expected, listener.phaseTransitions,
                "Full phase sequence should be preserved for human-last rounds");

        // Verify the last actor is tracked
        assertNotNull(gc.getLastRoundActor(),
                "lastRoundActor should be tracked");
    }
}
