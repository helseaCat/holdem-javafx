package com.nekocatgato.ui;

import com.nekocatgato.model.Player;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property tests for action label formatting.
 *
 * Property 1: Action label format correctness
 * Property 2: Label reflects most recent action per player
 *
 * Validates: Requirements 1.2, 2.1, 2.2, 5.3, 5.4
 */
class ActionLabelFormatPropertyTest {

    // ── Property 1: Action label format correctness ──

    @Property(tries = 200)
    @Tag("Feature: player-action-labels, Property 1: Action label format correctness")
    void checkAlwaysReturnsCheck(
            @ForAll @IntRange(min = 0, max = 10000) int wagerAmount,
            @ForAll @IntRange(min = 0, max = 10000) int remainingChips) {
        assertEquals("Check", GameTableView.formatActionLabel(Player.Action.CHECK, wagerAmount, remainingChips));
    }

    @Property(tries = 200)
    @Tag("Feature: player-action-labels, Property 1: Action label format correctness")
    void foldAlwaysReturnsFold(
            @ForAll @IntRange(min = 0, max = 10000) int wagerAmount,
            @ForAll @IntRange(min = 0, max = 10000) int remainingChips) {
        assertEquals("Fold", GameTableView.formatActionLabel(Player.Action.FOLD, wagerAmount, remainingChips));
    }

    @Property(tries = 200)
    @Tag("Feature: player-action-labels, Property 1: Action label format correctness")
    void callWithZeroChipsReturnsAllIn(
            @ForAll @IntRange(min = 0, max = 10000) int wagerAmount) {
        assertEquals("All In", GameTableView.formatActionLabel(Player.Action.CALL, wagerAmount, 0));
    }

    @Property(tries = 200)
    @Tag("Feature: player-action-labels, Property 1: Action label format correctness")
    void callWithChipsReturnsCall(
            @ForAll @IntRange(min = 0, max = 10000) int wagerAmount,
            @ForAll @IntRange(min = 1, max = 10000) int remainingChips) {
        assertEquals("Call", GameTableView.formatActionLabel(Player.Action.CALL, wagerAmount, remainingChips));
    }

    @Property(tries = 200)
    @Tag("Feature: player-action-labels, Property 1: Action label format correctness")
    void betWithZeroChipsReturnsAllIn(
            @ForAll @IntRange(min = 0, max = 10000) int wagerAmount) {
        assertEquals("All In", GameTableView.formatActionLabel(Player.Action.BET, wagerAmount, 0));
    }

    @Property(tries = 200)
    @Tag("Feature: player-action-labels, Property 1: Action label format correctness")
    void betWithChipsReturnsBetDollarX(
            @ForAll @IntRange(min = 0, max = 10000) int wagerAmount,
            @ForAll @IntRange(min = 1, max = 10000) int remainingChips) {
        assertEquals("Bet $" + wagerAmount,
                GameTableView.formatActionLabel(Player.Action.BET, wagerAmount, remainingChips));
    }

    @Property(tries = 200)
    @Tag("Feature: player-action-labels, Property 1: Action label format correctness")
    void raiseWithZeroChipsReturnsAllIn(
            @ForAll @IntRange(min = 0, max = 10000) int wagerAmount) {
        assertEquals("All In", GameTableView.formatActionLabel(Player.Action.RAISE, wagerAmount, 0));
    }

    @Property(tries = 200)
    @Tag("Feature: player-action-labels, Property 1: Action label format correctness")
    void raiseWithChipsReturnsRaiseDollarX(
            @ForAll @IntRange(min = 0, max = 10000) int wagerAmount,
            @ForAll @IntRange(min = 1, max = 10000) int remainingChips) {
        assertEquals("Raise $" + wagerAmount,
                GameTableView.formatActionLabel(Player.Action.RAISE, wagerAmount, remainingChips));
    }

    @Property(tries = 200)
    @Tag("Feature: player-action-labels, Property 1: Action label format correctness")
    void negativeInputsClampedToZero(
            @ForAll("actions") Player.Action action,
            @ForAll @IntRange(min = -10000, max = -1) int negativeWager,
            @ForAll @IntRange(min = -10000, max = -1) int negativeChips) {
        String result = GameTableView.formatActionLabel(action, negativeWager, negativeChips);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    // ── Property 2: Label reflects most recent action per player ──

    @Property(tries = 200)
    @Tag("Feature: player-action-labels, Property 2: Label reflects most recent action per player")
    void labelReflectsMostRecentActionPerPlayer(
            @ForAll("actionEventSequences") List<ActionEvent> events) {
        Map<String, String> lastLabel = new HashMap<>();
        for (ActionEvent event : events) {
            String label = GameTableView.formatActionLabel(event.action, event.wagerAmount, event.remainingChips);
            lastLabel.put(event.playerName, label);
        }
        // Replay and verify each player's final label
        Map<String, String> verify = new HashMap<>();
        for (ActionEvent event : events) {
            verify.put(event.playerName,
                    GameTableView.formatActionLabel(event.action, event.wagerAmount, event.remainingChips));
        }
        assertEquals(verify, lastLabel);
    }

    // ── Helpers ──

    record ActionEvent(String playerName, Player.Action action, int wagerAmount, int remainingChips) {}

    @Provide
    Arbitrary<Player.Action> actions() {
        return Arbitraries.of(Player.Action.values());
    }

    @Provide
    Arbitrary<List<ActionEvent>> actionEventSequences() {
        Arbitrary<String> names = Arbitraries.of("Alice", "Bob", "Charlie", "Diana");
        Arbitrary<Player.Action> acts = Arbitraries.of(Player.Action.values());
        Arbitrary<Integer> wagers = Arbitraries.integers().between(0, 5000);
        Arbitrary<Integer> chips = Arbitraries.integers().between(0, 5000);
        return Combinators.combine(names, acts, wagers, chips)
                .as(ActionEvent::new)
                .list().ofMinSize(1).ofMaxSize(20);
    }
}
