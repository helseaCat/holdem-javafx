package com.nekocatgato.engine;

import com.nekocatgato.model.Card;
import java.util.List;

public class HandEvaluator {

    public enum HandRank {
        HIGH_CARD, ONE_PAIR, TWO_PAIR, THREE_OF_A_KIND,
        STRAIGHT, FLUSH, FULL_HOUSE, FOUR_OF_A_KIND,
        STRAIGHT_FLUSH, ROYAL_FLUSH
    }

    /**
     * Evaluates the best 5-card hand from the given 7 cards (2 hole + 5 board).
     * Returns a HandRank representing the best hand.
     */
    public HandRank evaluate(List<Card> cards) {
        if (cards == null) {
            throw new IllegalArgumentException("cards must not be null");
        }
        if (cards.size() < 5) {
            throw new IllegalArgumentException("at least 5 cards required, got " + cards.size());
        }
        for (Card card : cards) {
            if (card == null) {
                throw new IllegalArgumentException("cards must not contain null elements");
            }
        }
        // TODO: implement full 7-card hand evaluation
        return HandRank.HIGH_CARD;
    }

    /**
     * Compares two hands. Returns positive if hand1 wins, negative if hand2 wins, 0 for tie.
     */
    public int compare(List<Card> hand1, List<Card> hand2) {
        return evaluate(hand1).ordinal() - evaluate(hand2).ordinal();
    }
}
