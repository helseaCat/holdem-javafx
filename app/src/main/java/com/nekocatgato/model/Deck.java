package com.nekocatgato.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Deck {
    private final List<Card> cards = new ArrayList<>();

    public Deck() {
        reset();
    }

    public void reset() {
        cards.clear();
        for (Card.Suit suit : Card.Suit.values())
            for (Card.Rank rank : Card.Rank.values())
                cards.add(new Card(suit, rank));
    }

    public void shuffle() {
        Collections.shuffle(cards);
    }

    public Card deal() {
        if (cards.isEmpty()) throw new IllegalStateException("Deck is empty");
        return cards.remove(cards.size() - 1);
    }

    public int size() { return cards.size(); }
}
