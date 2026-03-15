package com.nekocatgato.model;

import java.util.List;

public class GameState {
    public enum Phase { PRE_FLOP, FLOP, TURN, RIVER, SHOWDOWN }

    private Phase phase = Phase.PRE_FLOP;
    private int pot = 0;
    private final Board board = new Board();
    private final Deck deck = new Deck();

    public Phase getPhase() { return phase; }
    public void setPhase(Phase phase) { this.phase = phase; }

    public int getPot() { return pot; }
    public void addToPot(int amount) { this.pot += amount; }
    public void resetPot() { this.pot = 0; }

    public Board getBoard() { return board; }
    public Deck getDeck() { return deck; }
}
