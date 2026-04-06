package com.nekocatgato.ui;

// Bugfix: showdown-card-reveal, Properties 1 & 2

import com.nekocatgato.engine.HandEvaluator;
import net.jqwik.api.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property tests for the showdown card reveal bugfix.
 *
 * Property 1 (Bug Condition): formatHandRank produces correct display strings
 * Property 2 (Preservation): formatHandRank covers all HandRank enum values
 *
 * Note: Card persistence and hand rank label visibility tests require JavaFX
 * and are not included here. These tests cover the testable logic extracted
 * from GameTableView.
 *
 * Validates: Requirements 2.3, 2.4, 3.1, 3.2, 3.3, 3.4
 */
class ShowdownCardRevealPropertyTest {

    /**
     * Property: For all HandRank enum values, formatHandRank produces
     * a non-empty string with no underscores and proper title case.
     */
    @Property(tries = 50)
    void formatHandRankProducesValidDisplayString(
            @ForAll("handRanks") HandEvaluator.HandRank rank) {

        String result = GameTableView.formatHandRank(rank);

        assertNotNull(result, "formatHandRank should not return null");
        assertFalse(result.isEmpty(), "formatHandRank should not return empty string");
        assertFalse(result.contains("_"), "Display string should not contain underscores: " + result);

        // Each word should start with uppercase
        for (String word : result.split(" ")) {
            assertTrue(Character.isUpperCase(word.charAt(0)),
                    "Each word should start with uppercase: '" + word + "' in '" + result + "'");
            // Rest of word should be lowercase
            if (word.length() > 1) {
                String rest = word.substring(1);
                assertEquals(rest.toLowerCase(), rest,
                        "Rest of word should be lowercase: '" + word + "' in '" + result + "'");
            }
        }
    }

    @Provide
    Arbitrary<HandEvaluator.HandRank> handRanks() {
        return Arbitraries.of(HandEvaluator.HandRank.values());
    }

    // ── Unit tests for specific HandRank values ──

    @Test
    void formatHighCard() {
        assertEquals("High Card", GameTableView.formatHandRank(HandEvaluator.HandRank.HIGH_CARD));
    }

    @Test
    void formatOnePair() {
        assertEquals("One Pair", GameTableView.formatHandRank(HandEvaluator.HandRank.ONE_PAIR));
    }

    @Test
    void formatTwoPair() {
        assertEquals("Two Pair", GameTableView.formatHandRank(HandEvaluator.HandRank.TWO_PAIR));
    }

    @Test
    void formatThreeOfAKind() {
        assertEquals("Three Of A Kind", GameTableView.formatHandRank(HandEvaluator.HandRank.THREE_OF_A_KIND));
    }

    @Test
    void formatStraight() {
        assertEquals("Straight", GameTableView.formatHandRank(HandEvaluator.HandRank.STRAIGHT));
    }

    @Test
    void formatFlush() {
        assertEquals("Flush", GameTableView.formatHandRank(HandEvaluator.HandRank.FLUSH));
    }

    @Test
    void formatFullHouse() {
        assertEquals("Full House", GameTableView.formatHandRank(HandEvaluator.HandRank.FULL_HOUSE));
    }

    @Test
    void formatFourOfAKind() {
        assertEquals("Four Of A Kind", GameTableView.formatHandRank(HandEvaluator.HandRank.FOUR_OF_A_KIND));
    }

    @Test
    void formatStraightFlush() {
        assertEquals("Straight Flush", GameTableView.formatHandRank(HandEvaluator.HandRank.STRAIGHT_FLUSH));
    }

    @Test
    void formatRoyalFlush() {
        assertEquals("Royal Flush", GameTableView.formatHandRank(HandEvaluator.HandRank.ROYAL_FLUSH));
    }
}
