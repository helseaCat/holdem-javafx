package com.nekocatgato.model;

public abstract class Player {
    private final String name;
    private int chips;
    private int currentBet;
    private final Hand hand = new Hand();

    public Player(String name, int chips) {
        this.name = name;
        this.chips = chips;
    }

    public String getName() { return name; }
    public int getChips() { return chips; }
    public void setChips(int chips) { this.chips = chips; }
    public int getCurrentBet() { return currentBet; }
    public void setCurrentBet(int bet) { this.currentBet = bet; }
    public Hand getHand() { return hand; }

    public void bet(int amount) {
        chips -= amount;
        currentBet += amount;
    }

    public abstract Action decideAction(GameState state, int callAmount);

    public enum Action { FOLD, CHECK, CALL, RAISE }
}
