package com.nekocatgato.model;

public class HumanPlayer extends Player {
    private Action pendingAction;
    private int pendingRaiseAmount;

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
}
