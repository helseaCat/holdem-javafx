package com.nekocatgato.model;

public class AIPlayer extends Player {

    public AIPlayer(String name, int chips) {
        super(name, chips);
    }

    @Override
    public Action decideAction(GameState state, int callAmount) {
        // Simple rule-based AI: fold to large bets, otherwise call
        if (callAmount > getChips() / 2) return Action.FOLD;
        if (callAmount == 0) return Action.CHECK;
        return Action.CALL;
    }
}
