package com.nekocatgato.model;

import java.util.concurrent.CompletableFuture;

public class HumanPlayer extends Player {
    private Action pendingAction;
    private int pendingRaiseAmount;
    private volatile CompletableFuture<ActionResult> pendingFuture;

    public HumanPlayer(String name, int chips) {
        super(name, chips);
    }

    public void setPendingAction(Action action, int raiseAmount) {
        this.pendingAction = action;
        this.pendingRaiseAmount = raiseAmount;
    }

    public int getPendingRaiseAmount() { return pendingRaiseAmount; }

    @Override
    public Action decideAction(GameState state, int callAmount) {
        return pendingAction;
    }

    @Override
    public CompletableFuture<ActionResult> decideActionAsync(GameState state, int callAmount) {
        pendingFuture = new CompletableFuture<>();
        return pendingFuture;
    }

    /** Called from UI thread when player clicks an action button. */
    public void submitAction(Action action, int raiseAmount) {
        CompletableFuture<ActionResult> f = pendingFuture;
        if (f != null && !f.isDone()) {
            f.complete(new ActionResult(action, raiseAmount));
        }
    }
}
