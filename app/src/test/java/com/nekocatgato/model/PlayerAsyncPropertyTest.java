package com.nekocatgato.model;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.concurrent.CompletableFuture;

// Feature: async-game-loop, Property 3: All-AI no suspension
class PlayerAsyncPropertyTest {

    @Property(tries = 100)
    void aiPlayerDecideActionAsyncReturnsCompletedFuture(
            @ForAll @IntRange(min = 1, max = 10000) int chips,
            @ForAll @IntRange(min = 0, max = 5000) int callAmount) {

        AIPlayer ai = new AIPlayer("Bot", chips);
        GameState state = new GameState();

        CompletableFuture<ActionResult> future = ai.decideActionAsync(state, callAmount);

        assert future.isDone() : "AIPlayer future should be already completed";
        assert !future.isCompletedExceptionally() : "AIPlayer future should not be exceptional";
        assert !future.isCancelled() : "AIPlayer future should not be cancelled";

        ActionResult result = future.join();
        Player.Action syncAction = ai.decideAction(state, callAmount);

        assert result.action() == syncAction :
                "Async action should match sync action";
        assert result.raiseAmount() == 0 :
                "Default async wrapper should set raiseAmount to 0";
    }
}
