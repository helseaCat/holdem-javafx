package com.nekocatgato.model;

import java.util.ArrayList;
import java.util.List;

public class Board {
    private final List<Card> communityCards = new ArrayList<>();

    public void addCard(Card card) {
        communityCards.add(card);
    }

    public void clear() {
        communityCards.clear();
    }

    public List<Card> getCards() {
        return List.copyOf(communityCards);
    }
}
