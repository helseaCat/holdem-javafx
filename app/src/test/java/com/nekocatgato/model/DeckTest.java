package com.nekocatgato.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DeckTest {
    private Deck deck;

    @BeforeEach
    void setUp() {
        deck = new Deck();
    }

    @Test
    void newDeckHas52Cards() {
        assertEquals(52, deck.size());
    }

    @Test
    void dealReducesDeckSize() {
        deck.deal();
        assertEquals(51, deck.size());
    }

    @Test
    void dealingAllCardsProduces52UniqueCards() {
        Set<String> seen = new HashSet<>();
        while (deck.size() > 0) {
            Card c = deck.deal();
            String key = c.getSuit() + "-" + c.getRank();
            assertTrue(seen.add(key), "Duplicate card: " + key);
        }
        assertEquals(52, seen.size());
    }

    @Test
    void dealFromEmptyDeckThrows() {
        while (deck.size() > 0) deck.deal();
        assertThrows(IllegalStateException.class, () -> deck.deal());
    }

    @Test
    void resetRestoresDeckTo52() {
        deck.deal();
        deck.deal();
        deck.reset();
        assertEquals(52, deck.size());
    }

    @Test
    void shuffleChangesCardOrder() {
        Deck unshuffled = new Deck();
        deck.shuffle();
        // Collect both orders and compare — extremely unlikely to be equal after shuffle
        boolean different = false;
        for (int i = 0; i < 52; i++) {
            Card a = deck.deal();
            Card b = unshuffled.deal();
            if (a.getRank() != b.getRank() || a.getSuit() != b.getSuit()) {
                different = true;
                break;
            }
        }
        assertTrue(different, "Shuffled deck should differ from unshuffled");
    }
}
