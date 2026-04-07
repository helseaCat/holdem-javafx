package com.nekocatgato.engine;

import com.nekocatgato.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GameController async methods.
 * Validates: Requirements 5.5, 7.1, 7.3
 */
class GameControllerAsyncTest {

    private GameController controller;

    // ── Helpers ──

    static class ScriptedPlayer extends Player {
        private final Queue<Action> actions;

        ScriptedPlayer(String name, int chips, List<Action> actionList) {
            super(name, chips);
            this.actions = new LinkedList<>(actionList);
        }

        @Override
        public Action decideAction(GameState state, int callAmount) {
            return actions.isEmpty() ? Action.CALL : actions.poll();
        }
    }

    static class RecordingListener implements GameEventListener {
        final List<GameState.Phase> phases = new ArrayList<>();
        final List<String> eventLog = new ArrayList<>();
        boolean roundCompleted = false;
        Exception lastError = null;

        @Override
        public void onPhaseChanged(GameState.Phase phase, GameState state) {
            phases.add(phase);
            eventLog.add("phase:" + phase);
        }

        @Override
        public void onPlayerTurn(Player player, int callAmount) {
            eventLog.add("turn:" + player.getName());
        }

        @Override
        public void onPlayerActed(Player player, Player.Action action) {
            eventLog.add("acted:" + player.getName() + ":" + action);
        }

        @Override
        public void onRoundComplete(GameState state) {
            roundCompleted = true;
            eventLog.add("roundComplete");
        }

        @Override
        public void onError(Exception e) {
            lastError = e;
            eventLog.add("error:" + e.getClass().getSimpleName());
        }

        @Override
        public void onPlayerEliminated(Player player) {
            eventLog.add("eliminated:" + player.getName());
        }

        @Override
        public void onGameOver(Player winner) {
            eventLog.add("gameOver:" + (winner != null ? winner.getName() : "null"));
        }
    }

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

    @BeforeEach
    void setUp() {
        controller = new GameController();
        controller.setAiActionDelay(0, 0);
    }

    // ═══════════════════════════════════════════════════════════════════
    // startGameAsync validation tests (Requirement 5.5)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void startGameAsyncWithNullListThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.startGameAsync(null));
    }

    @Test
    void startGameAsyncWithTooFewPlayersThrows() {
        HumanPlayer solo = new HumanPlayer("Solo", 1000);
        assertThrows(IllegalArgumentException.class,
                () -> controller.startGameAsync(List.of(solo)));
    }

    @Test
    void startGameAsyncWithZeroHumanPlayersThrows() {
        AIPlayer ai1 = new AIPlayer("Bot1", 1000);
        AIPlayer ai2 = new AIPlayer("Bot2", 1000);
        assertThrows(IllegalArgumentException.class,
                () -> controller.startGameAsync(List.of(ai1, ai2)));
    }

    @Test
    void startGameAsyncWithTwoHumanPlayersThrows() {
        HumanPlayer h1 = new HumanPlayer("H1", 1000);
        HumanPlayer h2 = new HumanPlayer("H2", 1000);
        AIPlayer ai = new AIPlayer("Bot", 1000);
        assertThrows(IllegalArgumentException.class,
                () -> controller.startGameAsync(List.of(h1, h2, ai)));
    }

    @Test
    void startGameAsyncWithExactlyOneHumanSucceeds() {
        HumanPlayer human = new HumanPlayer("Human", 1000);
        AIPlayer ai = new AIPlayer("Bot", 1000);
        assertDoesNotThrow(
                () -> controller.startGameAsync(List.of(human, ai)));
    }

    // ═══════════════════════════════════════════════════════════════════
    // GameLoopInterruptedException propagation (Requirement 7.3)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void resolveActionThrowsGameLoopInterruptedOnInterrupt() {
        HumanPlayer human = new HumanPlayer("Human", 1000);
        AIPlayer ai = new AIPlayer("Bot", 1000);
        controller.startGame(List.of(human, ai));

        // Pre-set the interrupt flag. When resolveAction calls future.get()
        // on the incomplete HumanPlayer future, it will immediately throw
        // InterruptedException, which resolveAction wraps in GameLoopInterruptedException.
        // No threads or timing needed — fully deterministic.
        Thread.currentThread().interrupt();

        assertThrows(GameLoopInterruptedException.class,
                () -> controller.resolveAction(human, controller.getState(), 10));

        // Clean up interrupt flag in case it's still set
        Thread.interrupted();
    }

    @Test
    void gameLoopInterruptedExceptionWrapsInterruptedException() {
        InterruptedException cause = new InterruptedException("test");
        GameLoopInterruptedException ex = new GameLoopInterruptedException(cause);

        assertNotNull(ex.getCause());
        assertInstanceOf(InterruptedException.class, ex.getCause());
        assertEquals("Game loop interrupted", ex.getMessage());
    }

    // ═══════════════════════════════════════════════════════════════════
    // ActionResult record tests
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void actionResultRecordEquality() {
        ActionResult a = new ActionResult(Player.Action.CALL, 0);
        ActionResult b = new ActionResult(Player.Action.CALL, 0);
        ActionResult c = new ActionResult(Player.Action.RAISE, 50);

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void actionResultRecordAccessors() {
        ActionResult result = new ActionResult(Player.Action.RAISE, 100);
        assertEquals(Player.Action.RAISE, result.action());
        assertEquals(100, result.raiseAmount());
    }

    @Test
    void actionResultRecordToString() {
        ActionResult result = new ActionResult(Player.Action.FOLD, 0);
        String str = result.toString();
        assertTrue(str.contains("FOLD"));
        assertTrue(str.contains("0"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // resolveAction with exceptional futures (Requirement 7.1)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void resolveActionReturnsFoldOnCancellation() {
        Player cancelling = new Player("Cancel", 1000) {
            @Override
            public Action decideAction(GameState state, int callAmount) {
                return Action.CALL;
            }

            @Override
            public CompletableFuture<ActionResult> decideActionAsync(GameState state, int callAmount) {
                CompletableFuture<ActionResult> f = new CompletableFuture<>();
                f.cancel(true);
                return f;
            }
        };

        AIPlayer ai = new AIPlayer("Bot", 1000);
        controller.startGame(List.of(cancelling, ai));

        ActionResult result = controller.resolveAction(cancelling, controller.getState(), 10);
        assertEquals(Player.Action.FOLD, result.action());
        assertEquals(0, result.raiseAmount());
    }

    @Test
    void resolveActionReturnsFoldOnRuntimeException() {
        Player failing = new Player("Fail", 1000) {
            @Override
            public Action decideAction(GameState state, int callAmount) {
                return Action.CALL;
            }

            @Override
            public CompletableFuture<ActionResult> decideActionAsync(GameState state, int callAmount) {
                CompletableFuture<ActionResult> f = new CompletableFuture<>();
                f.completeExceptionally(new RuntimeException("boom"));
                return f;
            }
        };

        AIPlayer ai = new AIPlayer("Bot", 1000);
        controller.startGame(List.of(failing, ai));

        ActionResult result = controller.resolveAction(failing, controller.getState(), 10);
        assertEquals(Player.Action.FOLD, result.action());
    }

    // ═══════════════════════════════════════════════════════════════════
    // GameEventListener callback ordering for a known scenario
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void listenerCallbackOrderingForFullGame() {
        // Human calls everything, AI checks everything → game goes to showdown
        AutoHumanPlayer human = new AutoHumanPlayer("Human", 1000,
                new ArrayList<>(List.of(
                        Player.Action.CALL, Player.Action.CHECK,
                        Player.Action.CHECK, Player.Action.CHECK)));
        ScriptedPlayer ai = new ScriptedPlayer("Bot", 1000,
                List.of(Player.Action.CALL, Player.Action.CHECK,
                        Player.Action.CHECK, Player.Action.CHECK));

        RecordingListener listener = new RecordingListener();
        controller.setGameEventListener(listener);
        controller.startGame(List.of(human, ai));

        // Run a single hand directly (runGameLoop now loops until game over)
        controller.runSingleHand();

        // Verify phase ordering
        assertEquals(List.of(
                GameState.Phase.PRE_FLOP,
                GameState.Phase.FLOP,
                GameState.Phase.TURN,
                GameState.Phase.RIVER,
                GameState.Phase.SHOWDOWN), listener.phases);

        assertNull(listener.lastError, "No errors expected");

        // Verify the event log contains turn notifications for the human
        assertTrue(listener.eventLog.stream().anyMatch(e -> e.startsWith("turn:Human")),
                "Listener should be notified of human turns");

        // Verify acted events exist
        assertTrue(listener.eventLog.stream().anyMatch(e -> e.startsWith("acted:")),
                "Listener should be notified of player actions");
    }

    @Test
    void listenerReceivesErrorOnException() {
        // A player that throws during async resolution
        Player exploding = new Player("Explode", 1000) {
            @Override
            public Action decideAction(GameState state, int callAmount) {
                return Action.CALL;
            }

            @Override
            public CompletableFuture<ActionResult> decideActionAsync(GameState state, int callAmount) {
                throw new RuntimeException("unexpected explosion");
            }
        };

        ScriptedPlayer ai = new ScriptedPlayer("Bot", 1000,
                List.of(Player.Action.CALL));

        RecordingListener listener = new RecordingListener();
        controller.setGameEventListener(listener);
        controller.startGame(List.of(exploding, ai));
        controller.runGameLoop();

        assertNotNull(listener.lastError, "Listener should receive error notification");
    }
}
