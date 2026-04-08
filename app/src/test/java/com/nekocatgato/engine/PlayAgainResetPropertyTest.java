package com.nekocatgato.engine;

// Feature: multi-round-game, Property 9: Play Again resets to initial state

import com.nekocatgato.model.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Property 9: Play Again resets to initial state
 *
 * For any completed game (gameOver == true), calling resetAndRestart() shall
 * result in: (a) all original players restored to the players list, (b) each
 * player's chip count equal to the initial chip count, (c) isGameOver() returns
 * false, (d) getGameWinner() returns null, (e) the dealer button index reset
 * to -1. This is a round-trip property: start → play to completion → reset →
 * state equivalent to start.
 *
 * Validates: Requirements 5.2
 */
class PlayAgainResetPropertyTest {

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

    /** No-op listener to satisfy GameController's notification calls. */
    static class NoOpListener implements GameEventListener {
        @Override public void onPhaseChanged(GameState.Phase phase, GameState state) {}
        @Override public void onPlayerTurn(Player player, int callAmount) {}
        @Override public void onPlayerActed(Player player, Player.Action action) {}
        @Override public void onRoundComplete(GameState state) {}
        @Override public void onPlayerEliminated(Player player) {}
        @Override public void onGameOver(Player winner) {}
        @Override public void onError(Exception e) {}
    }

    /** Sets a private field on GameController via reflection. */
    private static void setField(GameController gc, String fieldName, Object value) throws Exception {
        Field field = GameController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(gc, value);
    }

    /**
     * Validates: Requirements 5.2
     *
     * Strategy:
     * 1. Start a game with N players (2-6) with random initial chips
     * 2. Simulate game completion: mutate player chips to random post-game
     *    values, eliminate some players, set gameOver = true via reflection
     * 3. Call resetAndRestart() — this synchronously resets state, calls
     *    nextRound(), then submits the game loop to the executor
     * 4. Immediately verify the synchronous state before the executor thread
     *    modifies anything further:
     *    (a) all original players restored
     *    (b) chip total = playerCount * initialChipCount (accounting for blinds)
     *    (c) isGameOver() == false
     *    (d) getGameWinner() == null
     *    (e) dealerButtonIndex == 0 (reset to -1, then nextRound advances to 0)
     * 5. Signal next round so the background game loop doesn't hang
     */
    @Property(tries = 100)
    void playAgainResetsToInitialState(
            @ForAll @IntRange(min = 2, max = 6) int playerCount,
            @ForAll @IntRange(min = 500, max = 5000) int initialChips,
            @ForAll Random random) throws Exception {

        // Build player list: 1 HumanPlayer + (playerCount-1) ScriptedAI
        // All players start with the same chip count
        // Using plain HumanPlayer (not auto-submitting) so the game loop blocks
        // on decideActionAsync, preventing a race between the executor thread
        // and our assertions.
        HumanPlayer human = new HumanPlayer("Human", initialChips);

        List<Player> playerList = new ArrayList<>();
        playerList.add(human);
        for (int i = 1; i < playerCount; i++) {
            playerList.add(new ScriptedAI("AI" + i, initialChips));
        }

        GameController gc = new GameController();
        gc.setGameEventListener(new NoOpListener());
        gc.setAiActionDelay(0, 0);

        // Record original player names
        List<String> originalNames = new ArrayList<>();
        for (Player p : playerList) {
            originalNames.add(p.getName());
        }

        // Start game synchronously (no executor, no game loop)
        gc.startGame(playerList);

        // Verify initial state was captured correctly
        assert gc.getInitialChipCount() == initialChips :
                "initialChipCount should be " + initialChips;
        assert gc.getAllOriginalPlayers().size() == playerCount :
                "allOriginalPlayers should have " + playerCount + " players";

        // ── Simulate a completed game ──

        // Mutate chips to random post-game values
        List<Player> players = gc.getPlayers();
        human.setChips(initialChips + random.nextInt(initialChips)); // human won chips

        for (int i = 1; i < players.size(); i++) {
            // Some AI players lose all chips (eliminated), others have random amounts
            if (random.nextBoolean()) {
                players.get(i).setChips(0);
            } else {
                players.get(i).setChips(random.nextInt(initialChips) + 1);
            }
        }

        // Eliminate broke players (removes 0-chip players from list)
        gc.eliminateBrokePlayers();

        // Set game-over state via reflection
        Player fakeWinner = gc.getPlayers().get(0);
        setField(gc, "gameOver", true);
        setField(gc, "gameWinner", fakeWinner);

        // Advance dealer index to simulate multiple rounds played
        int fakeDealer = random.nextInt(gc.getPlayers().size());
        setField(gc, "dealerButtonIndex", fakeDealer);

        // Confirm game-over state before reset
        assert gc.isGameOver() : "gameOver should be true before reset";
        assert gc.getGameWinner() != null : "gameWinner should be set before reset";
        assert gc.getPlayers().size() <= playerCount :
                "Some players should have been eliminated";

        // ── Call resetAndRestart() ──
        // This synchronously: restores players, resets chips, clears gameOver,
        // sets dealerButtonIndex = -1, calls nextRound() (advances dealer to 0,
        // posts blinds, deals cards), then submits game loop to executor.
        gc.resetAndRestart();

        // ── Verify post-conditions (checked immediately, before executor runs) ──

        // (a) All original players restored to the players list
        List<Player> resetPlayers = gc.getPlayers();
        assert resetPlayers.size() == playerCount :
                "Expected " + playerCount + " players after reset, got " + resetPlayers.size();

        Set<String> resetNames = new HashSet<>();
        for (Player p : resetPlayers) {
            resetNames.add(p.getName());
        }
        for (String name : originalNames) {
            assert resetNames.contains(name) :
                    "Player '" + name + "' should be restored after reset";
        }

        // (b) Each player's chip count equal to the initial chip count
        //     After nextRound() inside resetAndRestart(), blinds have been posted.
        //     Total chips across all players + pot must equal playerCount * initialChips.
        int totalChips = 0;
        for (Player p : resetPlayers) {
            totalChips += p.getChips();
        }
        int pot = gc.getState().getPot();
        int expectedTotal = playerCount * initialChips;
        assert totalChips + pot == expectedTotal :
                "Total chips should be " + expectedTotal +
                " but got chips=" + totalChips + " + pot=" + pot +
                " = " + (totalChips + pot);

        // (c) isGameOver() returns false
        assert !gc.isGameOver() :
                "isGameOver() should be false after resetAndRestart()";

        // (d) getGameWinner() returns null
        assert gc.getGameWinner() == null :
                "getGameWinner() should be null after resetAndRestart()";

        // (e) Dealer button index: resetAndRestart sets to -1, then nextRound
        //     advances to (-1 + 1) % size = 0. Equivalent to a fresh start.
        assert gc.getDealerButtonIndex() == 0 :
                "Dealer button index should be 0 after reset+nextRound, got " +
                gc.getDealerButtonIndex();

        // Clean up: signal next round so the background game loop doesn't hang
        gc.signalNextRound();
    }
}
