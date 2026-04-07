package com.nekocatgato.engine;

// Feature: multi-round-game, Property 11: Blinds posted to correct positions relative to dealer

import com.nekocatgato.model.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Property 11: Blinds posted to correct positions relative to dealer
 *
 * For all rounds in a game loop, the small blind shall be posted by the player
 * at index (dealerButtonIndex + 1) % players.size() and the big blind by the
 * player at index (dealerButtonIndex + 2) % players.size(), wrapping around
 * the table.
 *
 * Validates: Requirements 8.3
 */
class BlindsPositionPropertyTest {

    /** HumanPlayer that auto-submits CALL so hands complete without blocking. */
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

    /** AI that always calls/checks — deterministic, never folds, never raises. */
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
     * Listener that captures player bets when PRE_FLOP phase fires,
     * and tracks round completion / game-over for coordination.
     */
    static class BlindCapturingListener implements GameEventListener {
        final AtomicReference<Map<String, Integer>> capturedBets = new AtomicReference<>();
        final AtomicBoolean gameOverFired = new AtomicBoolean(false);
        volatile CountDownLatch preFlopLatch;
        volatile CountDownLatch roundCompleteLatch;
        private volatile List<Player> playersSnapshot;
        private volatile int dealerIndexSnapshot;
        private volatile GameController gc;

        BlindCapturingListener() {
            preFlopLatch = new CountDownLatch(1);
            roundCompleteLatch = new CountDownLatch(1);
        }

        void setController(GameController gc) { this.gc = gc; }

        void resetLatches() {
            preFlopLatch = new CountDownLatch(1);
            roundCompleteLatch = new CountDownLatch(1);
            capturedBets.set(null);
        }

        @Override
        public void onPhaseChanged(GameState.Phase phase, GameState state) {
            if (phase == GameState.Phase.PRE_FLOP && gc != null) {
                // Capture bets at PRE_FLOP — blinds have just been posted
                Map<String, Integer> bets = new LinkedHashMap<>();
                List<Player> players = gc.getPlayers();
                for (Player p : players) {
                    bets.put(p.getName(), p.getCurrentBet());
                }
                capturedBets.set(bets);
                dealerIndexSnapshot = gc.getDealerButtonIndex();
                playersSnapshot = new ArrayList<>(players);
                preFlopLatch.countDown();
            }
        }

        @Override public void onPlayerTurn(Player player, int callAmount) {}
        @Override public void onPlayerActed(Player player, Player.Action action) {}

        @Override
        public void onRoundComplete(GameState state) {
            roundCompleteLatch.countDown();
        }

        @Override public void onPlayerEliminated(Player player) {}

        @Override
        public void onGameOver(Player winner) {
            gameOverFired.set(true);
            preFlopLatch.countDown();
            roundCompleteLatch.countDown();
        }

        @Override public void onError(Exception e) {}

        int getDealerIndexSnapshot() { return dealerIndexSnapshot; }
        List<Player> getPlayersSnapshot() { return playersSnapshot; }
    }

    /**
     * Validates: Requirements 8.3
     *
     * Generate random player counts (2-6), start the async game loop,
     * wait for PRE_FLOP phase, then verify that the player at
     * (dealerIndex + 1) % size has the small blind bet and the player at
     * (dealerIndex + 2) % size has the big blind bet.
     *
     * Runs across multiple rounds to verify blinds rotate correctly.
     */
    @Property(tries = 100)
    void blindsPostedToCorrectPositionsRelativeToDealer(
            @ForAll @IntRange(min = 2, max = 6) int playerCount,
            @ForAll @IntRange(min = 1, max = 3) int roundsToCheck) throws Exception {

        int chips = 100_000; // high chips so nobody gets eliminated

        AutoHumanPlayer human = new AutoHumanPlayer("Human", chips);
        List<Player> playerList = new ArrayList<>();
        playerList.add(human);
        for (int i = 1; i < playerCount; i++) {
            playerList.add(new ScriptedAI("AI" + i, chips));
        }

        BlindCapturingListener listener = new BlindCapturingListener();
        GameController gc = new GameController();
        listener.setController(gc);
        gc.setGameEventListener(listener);

        gc.setAiActionDelay(0, 0);
        gc.startGameAsync(playerList);

        for (int round = 1; round <= roundsToCheck; round++) {
            // Wait for PRE_FLOP phase to fire (blinds posted)
            boolean preFlopFired = listener.preFlopLatch.await(10, TimeUnit.SECONDS);

            if (listener.gameOverFired.get()) {
                return; // game ended — skip
            }

            assert preFlopFired : "PRE_FLOP should fire within 10 seconds (round " + round + ")";

            // Get captured data
            Map<String, Integer> bets = listener.capturedBets.get();
            int dealerIdx = listener.getDealerIndexSnapshot();
            List<Player> players = listener.getPlayersSnapshot();

            assert bets != null : "Bets should have been captured at PRE_FLOP";
            assert players != null : "Players snapshot should have been captured";

            int numPlayers = players.size();
            int sbIndex = (dealerIdx + 1) % numPlayers;
            int bbIndex = (dealerIdx + 2) % numPlayers;

            Player sbPlayer = players.get(sbIndex);
            Player bbPlayer = players.get(bbIndex);

            int sbBet = bets.get(sbPlayer.getName());
            int bbBet = bets.get(bbPlayer.getName());

            assert sbBet == GameController.SMALL_BLIND :
                    "Round " + round + ": Small blind player '" + sbPlayer.getName() +
                    "' at index " + sbIndex + " (dealer=" + dealerIdx +
                    ") should have bet " + GameController.SMALL_BLIND +
                    " but bet " + sbBet;

            assert bbBet == GameController.BIG_BLIND :
                    "Round " + round + ": Big blind player '" + bbPlayer.getName() +
                    "' at index " + bbIndex + " (dealer=" + dealerIdx +
                    ") should have bet " + GameController.BIG_BLIND +
                    " but bet " + bbBet;

            // Verify no other player has a non-zero bet at PRE_FLOP start
            for (int i = 0; i < numPlayers; i++) {
                if (i != sbIndex && i != bbIndex) {
                    Player p = players.get(i);
                    int bet = bets.get(p.getName());
                    assert bet == 0 :
                            "Round " + round + ": Player '" + p.getName() +
                            "' at index " + i + " should have 0 bet but has " + bet;
                }
            }

            // Wait for round to complete, then signal next round
            if (round < roundsToCheck) {
                boolean roundDone = listener.roundCompleteLatch.await(10, TimeUnit.SECONDS);
                if (listener.gameOverFired.get()) {
                    return;
                }
                assert roundDone : "Round " + round + " should complete within 10 seconds";

                // Wait for engine to create the future (poll instead of sleep)
                for (int i = 0; i < 50; i++) {
                    CompletableFuture<Void> f = gc.getNextRoundFuture();
                    if (f != null && !f.isDone()) break;
                    Thread.sleep(10);
                }
                listener.resetLatches();
                gc.signalNextRound();
            }
        }

        // Cleanup: signal to unblock engine if waiting
        gc.signalNextRound();
    }
}
