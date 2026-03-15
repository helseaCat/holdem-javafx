package com.nekocatgato.model;

import java.util.ArrayList;
import java.util.List;

public class Hand {
    private final List<Card> holeCards = new ArrayList<>();

    public void addCard(Card card) {
        holeCards.add(card);
    }

    public void clear() {
        holeCards.clear();
    }

    public List<Card> getCards() {
        return List.copyOf(holeCards);
    }
}
