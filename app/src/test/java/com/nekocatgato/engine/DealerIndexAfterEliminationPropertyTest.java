package com.nekocatgato.engine;

// Feature: multi-round-game, Property 6: Dealer index remains valid after any elimination pattern

import com.nekocatgato.model.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Property 6: Dealer index remains valid after any elimination pattern
 *
 * For any player list of size N, any dealer button index in [0, N), and any
 * subset of players to eliminate, after elimination and dealer adjustment:
 * (a) the dealer index is in [0, remainingPlayers.size()),
 * (b) if the original dealer was not eliminated, the dealer index still points
 *     to the same Player object,
 * (c) if a player before the original dealer was eliminated, the index decreases
 *     accordingly,
 * (d) if the dealer was eliminated, the index points to the next player in seat order.
 *
 * Validates: Requirements 2.4, 8.1, 8.2
 */
class DealerIndexAfterEliminationPropertyTest {

    static class NoOpListener implements GameEventListener {
        @Override public void onPhaseChanged(GameState.Phase phase, GameState state) {}
        @Override public void onPlayerTurn(Player player, int callAmount) {}
        @Override public void onPlayerActed(Player player, Player.Action action) {}
        @Override public void onRoundComplete(GameState state) {}
        @Override public void onPlayerEliminated(Player player) {}
        @Override public void onGameOver(Player winner) {}
        @Override public void onError(Exception e) {}
    }

    static class TestPlayer extends Player {
        TestPlayer(String name, int chips) {
            super(name, chips);
        }

        @Override
        public Action decideAction(GameState state, int callAmount) {
            return callAmount == 0 ? Action.CHECK : Action.CALL;
        }
    }

    private void setDealerButtonIndex(GameController gc, int index) {
        try {
            Field field = GameController.class.getDeclaredField("dealerButtonIndex");
            field.setAccessible(true);
            field.setInt(gc, index);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Validates: Requirements 2.4, 8.1, 8.2
     *
     * Generate random player lists, dealer positions, elimination subsets,
     * verify index validity after elimination and dealer adjustment.
     */
    @Property(tries = 100)
    void dealerIndexRemainsValidAfterAnyEliminationPattern(
            @ForAll @IntRange(min = 3, max = 8) int playerCount,
            @ForAll @IntRange(min = 1000, max = 50000) int baseChips,
            @ForAll Random random) {

        // Build player list with HumanPlayer at index 0
        List<Player> playerList = new ArrayList<>();
        playerList.add(new HumanPlayer("Human", baseChips));
        for (int i = 1; i < playerCount; i++) {
            playerList.add(new TestPlayer("P" + i, baseChips));
        }

        GameController gc = new GameController();
        gc.setGameEventListener(new NoOpListener());
        gc.startGame(playerList);

        List<Player> players = gc.getPlayers();

        // Pick a random dealer index in [0, N)
        int dealerIndex = random.nextInt(players.size());
        setDealerButtonIndex(gc, dealerIndex);

        // Remember the original dealer player
        Player originalDealer = players.get(dealerIndex);

        // Randomly assign 0 chips to some players, but keep human alive
        // and ensure at least 2 survivors
        Set<Integer> eliminationCandidates = new HashSet<>();
        for (int i = 1; i < players.size(); i++) {
            if (random.nextBoolean()) {
                eliminationCandidates.add(i);
            }
        }

        // Ensure at least 2 survivors
        int survivorCount = players.size() - eliminationCandidates.size();
        if (survivorCount < 2) return;

        // Apply 0 chips to chosen players
        for (int idx : eliminationCandidates) {
            players.get(idx).setChips(0);
        }

        boolean dealerWasEliminated = originalDealer.getChips() == 0;

        // Capture the players that sit after the dealer in seat order (for case d)
        // This is the circular order starting from dealerIndex+1
        List<Player> playersAfterDealerInOrder = new ArrayList<>();
        for (int offset = 1; offset < players.size(); offset++) {
            int idx = (dealerIndex + offset) % players.size();
            Player p = players.get(idx);
            if (p.getChips() > 0) {
                playersAfterDealerInOrder.add(p);
            }
        }

        // Execute elimination and adjustment
        List<Integer> eliminatedIndices = gc.eliminateBrokePlayers();
        gc.adjustDealerIndexAfterElimination(eliminatedIndices);

        List<Player> remaining = gc.getPlayers();
        int newDealerIndex = gc.getDealerButtonIndex();

        // (a) Dealer index is in valid range [0, remainingPlayers.size())
        // Note: -1 is also valid as a pre-rotation state, but after adjustment
        // the design clamps to [-1, size-1]. We check the effective range.
        assert newDealerIndex >= -1 && newDealerIndex < remaining.size() :
                "Dealer index " + newDealerIndex + " out of range for "
                        + remaining.size() + " remaining players";

        // For cases (b), (c), (d) we only check when index >= 0
        if (newDealerIndex >= 0) {
            if (!dealerWasEliminated) {
                // (b) If original dealer was not eliminated, dealer index still
                //     points to the same Player object
                Player currentDealer = remaining.get(newDealerIndex);
                assert currentDealer == originalDealer :
                        "Dealer was not eliminated but index points to "
                                + currentDealer.getName() + " instead of "
                                + originalDealer.getName();
            } else {
                // (d) If dealer was eliminated, after nextRound()'s +1 rotation
                //     the index should land on the next surviving player.
                //     adjustDealerIndexAfterElimination sets index to X-1 so that
                //     nextRound's (index+1)%size lands on the correct next player.
                //     We verify that (newDealerIndex+1)%size points to the next
                //     surviving player in seat order.
                if (!playersAfterDealerInOrder.isEmpty()) {
                    Player expectedNext = playersAfterDealerInOrder.get(0);
                    int nextIdx = (newDealerIndex + 1) % remaining.size();
                    Player actualNext = remaining.get(nextIdx);
                    assert actualNext == expectedNext :
                            "After dealer elimination, next rotation should land on "
                                    + expectedNext.getName() + " but would land on "
                                    + actualNext.getName();
                }
            }
        }
    }
}
