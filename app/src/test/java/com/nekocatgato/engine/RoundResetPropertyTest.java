package com.nekocatgato.engine;

// Feature: multi-round-game, Property 3: Round reset preserves chip counts

import com.nekocatgato.model.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Property 3: Round reset preserves chip counts
 *
 * For any round transition, calling nextRound() shall result in:
 * (a) each player's chip count unchanged from the end of the previous round (before blind posting),
 * (b) the board empty,
 * (c) all player bets at 0,
 * (d) all player hands cleared,
 * (e) each player dealt exactly 2 new hole cards.
 * The total chip count across all players must equal the total before the round reset
 * (pot is 0 at round boundary).
 *
 * Validates: Requirements 1.3
 */
class RoundResetPropertyTest {

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

    // ── Helper: AI that always calls/checks ──

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
     * Listener that captures the game state snapshot at the start of the second
     * round's PRE_FLOP phase. This runs on the engine thread, so the snapshot
     * is taken at the exact moment after nextRound() completes but before
     * runSingleHand() modifies anything further.
     */
    static class SnapshotListener implements GameEventListener {
        final CountDownLatch firstRoundLatch = new CountDownLatch(1);
        final CountDownLatch snapshotTakenLatch = new CountDownLatch(1);
        final AtomicBoolean gameOverFired = new AtomicBoolean(false);
        final AtomicInteger preFlopCount = new AtomicInteger(0);

        // Snapshot captured at second PRE_FLOP
        volatile int snapshotTotalChips = -1;
        volatile int snapshotPot = -1;
        volatile int snapshotBoardSize = -1;
        volatile List<Integer> snapshotHandSizes = null;

        private List<Player> playersRef;
        private GameState stateRef;

        void setRefs(List<Player> players, GameState state) {
            this.playersRef = players;
            this.stateRef = state;
        }

        @Override
        public void onPhaseChanged(GameState.Phase phase, GameState state) {
            if (phase == GameState.Phase.PRE_FLOP) {
                int count = preFlopCount.incrementAndGet();
                if (count == 2 && playersRef != null) {
                    // Capture snapshot on the engine thread — no race
                    int totalChips = 0;
                    List<Integer> handSizes = new ArrayList<>();
                    for (Player p : playersRef) {
                        totalChips += p.getChips();
                        handSizes.add(p.getHand().getCards().size());
                    }
                    snapshotTotalChips = totalChips;
                    snapshotPot = stateRef.getPot();
                    snapshotBoardSize = stateRef.getBoard().getCards().size();
                    snapshotHandSizes = handSizes;
                    snapshotTakenLatch.countDown();
                }
            }
        }

        @Override public void onPlayerTurn(Player player, int callAmount) {}
        @Override public void onPlayerActed(Player player, Player.Action action, int wagerAmount) {}
        @Override public void onRoundComplete(GameState state) { firstRoundLatch.countDown(); }
        @Override public void onPlayerEliminated(Player player) {}
        @Override public void onGameOver(Player winner) { gameOverFired.set(true); }
        @Override public void onError(Exception e) {}
    }

    /**
     * Validates: Requirements 1.3
     *
     * Strategy: Run a full hand via the async game loop, capture chip totals after
     * the round completes (pot is 0 at round boundary), signal next round, then
     * capture a snapshot inside the onPhaseChanged(PRE_FLOP) callback on the engine
     * thread — this is the exact moment after nextRound() finishes. Verify:
     * - Total chips conserved (chips + pot = total before)
     * - Board is empty
     * - Each player has exactly 2 hole cards
     */
    @Property(tries = 100)
    void roundResetPreservesChipCounts(
            @ForAll @IntRange(min = 2, max = 6) int playerCount,
            @ForAll @IntRange(min = 5000, max = 50000) int chipAmount) throws Exception {

        // Build player list: 1 AutoHuman + (playerCount-1) ScriptedAI
        // High chip amounts ensure nobody gets eliminated in one hand
        AutoHumanPlayer human = new AutoHumanPlayer("Human", chipAmount);

        List<Player> playerList = new ArrayList<>();
        playerList.add(human);
        for (int i = 1; i < playerCount; i++) {
            playerList.add(new ScriptedAI("AI" + i, chipAmount));
        }

        GameController gc = new GameController();
        SnapshotListener listener = new SnapshotListener();
        gc.setGameEventListener(listener);

        // Start the game (runs first hand asynchronously)
        gc.setAiActionDelay(0, 0);
        gc.startGameAsync(playerList);

        // Give the listener access to the player list and state for snapshotting
        listener.setRefs(gc.getPlayers(), gc.getState());

        // Wait for the first round to complete
        boolean roundDone = listener.firstRoundLatch.await(5, TimeUnit.SECONDS);
        assert roundDone : "onRoundComplete should have fired within 5 seconds";

        // Wait for engine to create the future (poll instead of sleep)
        for (int i = 0; i < 50; i++) {
            CompletableFuture<Void> f = gc.getNextRoundFuture();
            if (f != null && !f.isDone()) break;
            Thread.sleep(10);
        }

        // If game ended (someone eliminated), skip — not a valid round transition
        if (listener.gameOverFired.get()) {
            return;
        }

        // ── Capture total chips AFTER round completes, BEFORE nextRound() ──
        // At round boundary: pot is 0 (awarded to winner)
        int totalChipsBefore = 0;
        for (Player p : gc.getPlayers()) {
            totalChipsBefore += p.getChips();
        }

        // Signal next round — triggers nextRound() on the engine thread
        gc.signalNextRound();

        // Wait for the snapshot to be taken inside onPhaseChanged(PRE_FLOP)
        boolean snapshotTaken = listener.snapshotTakenLatch.await(5, TimeUnit.SECONDS);
        assert snapshotTaken : "Snapshot should have been taken at second PRE_FLOP";

        // ── Verify snapshot captured at the exact moment after nextRound() ──

        // (a) Total chip conservation: chips + pot after nextRound must equal total before
        assert listener.snapshotTotalChips + listener.snapshotPot == totalChipsBefore :
                "Total chips must be conserved. Before: " + totalChipsBefore +
                ", After (chips=" + listener.snapshotTotalChips +
                " + pot=" + listener.snapshotPot + "): " +
                (listener.snapshotTotalChips + listener.snapshotPot);

        // (b) Board must be empty after nextRound()
        assert listener.snapshotBoardSize == 0 :
                "Board should be empty after nextRound(), but had " +
                listener.snapshotBoardSize + " cards";

        // (e) Each player must have exactly 2 hole cards after nextRound()
        for (int i = 0; i < listener.snapshotHandSizes.size(); i++) {
            assert listener.snapshotHandSizes.get(i) == 2 :
                    "Player " + i + " should have exactly 2 hole cards, but had " +
                    listener.snapshotHandSizes.get(i);
        }

        // Clean up: signal again so the engine thread doesn't hang
        gc.signalNextRound();
    }
}
