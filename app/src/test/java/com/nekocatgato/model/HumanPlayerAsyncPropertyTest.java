package com.nekocatgato.model;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.concurrent.CompletableFuture;

class HumanPlayerAsyncPropertyTest {

    // Feature: async-game-loop, Property 1: HumanPlayer async action round-trip
    @Property(tries = 100)
    void decideActionAsyncReturnsIncompleteFutureThatSubmitActionCompletes(
            @ForAll("actions") Player.Action action,
            @ForAll @IntRange(min = 0, max = 1000) int raiseAmount) {

        HumanPlayer human = new HumanPlayer("Human", 1000);
        GameState state = new GameState();

        CompletableFuture<ActionResult> future = human.decideActionAsync(state, 0);

        assert !future.isDone() : "Future should be incomplete before submitAction";

        human.submitAction(action, raiseAmount);

        assert future.isDone() : "Future should be complete after submitAction";
        assert !future.isCompletedExceptionally() : "Future should not be exceptional";
        assert !future.isCancelled() : "Future should not be cancelled";

        ActionResult result = future.join();
        assert result.action() == action :
                "Round-tripped action should match: expected " + action + " got " + result.action();
        assert result.raiseAmount() == raiseAmount :
                "Round-tripped raiseAmount should match: expected " + raiseAmount + " got " + result.raiseAmount();
    }

    // Feature: async-game-loop, Property 5: At-most-once completion
    @Property(tries = 100)
    void submitActionOnlyCompletesTheFutureOnce(
            @ForAll("actions") Player.Action firstAction,
            @ForAll @IntRange(min = 0, max = 1000) int firstRaise,
            @ForAll("actions") Player.Action secondAction,
            @ForAll @IntRange(min = 0, max = 1000) int secondRaise) {

        HumanPlayer human = new HumanPlayer("Human", 1000);
        GameState state = new GameState();

        CompletableFuture<ActionResult> future = human.decideActionAsync(state, 0);

        human.submitAction(firstAction, firstRaise);
        human.submitAction(secondAction, secondRaise);

        ActionResult result = future.join();
        assert result.action() == firstAction :
                "Only the first submitted action should win: expected " + firstAction + " got " + result.action();
        assert result.raiseAmount() == firstRaise :
                "Only the first submitted raiseAmount should win: expected " + firstRaise + " got " + result.raiseAmount();
    }

    @Provide
    Arbitrary<Player.Action> actions() {
        return Arbitraries.of(Player.Action.values());
    }
}
