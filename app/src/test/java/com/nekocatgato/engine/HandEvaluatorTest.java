package com.nekocatgato.engine;

import com.nekocatgato.model.Card;
import com.nekocatgato.model.Card.Suit;
import com.nekocatgato.model.Card.Rank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.nekocatgato.engine.HandEvaluator.HandRank.*;
import static org.junit.jupiter.api.Assertions.*;

class HandEvaluatorTest {
    private HandEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new HandEvaluator();
    }

    // --- Helper ---
    private Card c(Suit s, Rank r) { return new Card(s, r); }

    // --- Input Validation ---
    @Test
    void nullListThrows() {
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(null));
    }

    @Test
    void tooFewCardsThrows() {
        List<Card> cards = List.of(
            c(Suit.HEARTS, Rank.ACE), c(Suit.DIAMONDS, Rank.KING),
            c(Suit.CLUBS, Rank.QUEEN), c(Suit.SPADES, Rank.JACK)
        );
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(cards));
    }

    @Test
    void listWithNullElementThrows() {
        List<Card> cards = new java.util.ArrayList<>();
        cards.add(c(Suit.HEARTS, Rank.ACE));
        cards.add(null);
        cards.add(c(Suit.CLUBS, Rank.QUEEN));
        cards.add(c(Suit.SPADES, Rank.JACK));
        cards.add(c(Suit.DIAMONDS, Rank.TEN));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(cards));
    }

    // --- High Card ---
    @Test
    void highCard() {
        List<Card> cards = List.of(
            c(Suit.HEARTS, Rank.TWO), c(Suit.DIAMONDS, Rank.FIVE),
            c(Suit.CLUBS, Rank.SEVEN), c(Suit.SPADES, Rank.NINE),
            c(Suit.HEARTS, Rank.JACK), c(Suit.DIAMONDS, Rank.KING),
            c(Suit.CLUBS, Rank.ACE)
        );
        assertEquals(HIGH_CARD, evaluator.evaluate(cards));
    }

    // --- One Pair ---
    @Test
    void onePair() {
        List<Card> cards = List.of(
            c(Suit.HEARTS, Rank.ACE), c(Suit.DIAMONDS, Rank.ACE),
            c(Suit.CLUBS, Rank.TWO), c(Suit.SPADES, Rank.FIVE),
            c(Suit.HEARTS, Rank.SEVEN), c(Suit.DIAMONDS, Rank.NINE),
            c(Suit.CLUBS, Rank.JACK)
        );
        assertEquals(ONE_PAIR, evaluator.evaluate(cards));
    }

    // --- Two Pair ---
    @Test
    void twoPair() {
        List<Card> cards = List.of(
            c(Suit.HEARTS, Rank.ACE), c(Suit.DIAMONDS, Rank.ACE),
            c(Suit.CLUBS, Rank.KING), c(Suit.SPADES, Rank.KING),
            c(Suit.HEARTS, Rank.TWO), c(Suit.DIAMONDS, Rank.FIVE),
            c(Suit.CLUBS, Rank.SEVEN)
        );
        assertEquals(TWO_PAIR, evaluator.evaluate(cards));
    }

    // --- Three of a Kind ---
    @Test
    void threeOfAKind() {
        List<Card> cards = List.of(
            c(Suit.HEARTS, Rank.QUEEN), c(Suit.DIAMONDS, Rank.QUEEN),
            c(Suit.CLUBS, Rank.QUEEN), c(Suit.SPADES, Rank.TWO),
            c(Suit.HEARTS, Rank.FIVE), c(Suit.DIAMONDS, Rank.SEVEN),
            c(Suit.CLUBS, Rank.NINE)
        );
        assertEquals(THREE_OF_A_KIND, evaluator.evaluate(cards));
    }

    // --- Straight ---
    @Test
    void straight() {
        List<Card> cards = List.of(
            c(Suit.HEARTS, Rank.FIVE), c(Suit.DIAMONDS, Rank.SIX),
            c(Suit.CLUBS, Rank.SEVEN), c(Suit.SPADES, Rank.EIGHT),
            c(Suit.HEARTS, Rank.NINE), c(Suit.DIAMONDS, Rank.TWO),
            c(Suit.CLUBS, Rank.KING)
        );
        assertEquals(STRAIGHT, evaluator.evaluate(cards));
    }

    @Test
    void wheelStraightAceLow() {
        // A-2-3-4-5 straight
        List<Card> cards = List.of(
            c(Suit.HEARTS, Rank.ACE), c(Suit.DIAMONDS, Rank.TWO),
            c(Suit.CLUBS, Rank.THREE), c(Suit.SPADES, Rank.FOUR),
            c(Suit.HEARTS, Rank.FIVE), c(Suit.DIAMONDS, Rank.KING),
            c(Suit.CLUBS, Rank.QUEEN)
        );
        assertEquals(STRAIGHT, evaluator.evaluate(cards));
    }

    // --- Flush ---
    @Test
    void flush() {
        List<Card> cards = List.of(
            c(Suit.HEARTS, Rank.TWO), c(Suit.HEARTS, Rank.FIVE),
            c(Suit.HEARTS, Rank.SEVEN), c(Suit.HEARTS, Rank.NINE),
            c(Suit.HEARTS, Rank.JACK), c(Suit.DIAMONDS, Rank.KING),
            c(Suit.CLUBS, Rank.ACE)
        );
        assertEquals(FLUSH, evaluator.evaluate(cards));
    }

    // --- Full House ---
    @Test
    void fullHouse() {
        List<Card> cards = List.of(
            c(Suit.HEARTS, Rank.TEN), c(Suit.DIAMONDS, Rank.TEN),
            c(Suit.CLUBS, Rank.TEN), c(Suit.SPADES, Rank.KING),
            c(Suit.HEARTS, Rank.KING), c(Suit.DIAMONDS, Rank.TWO),
            c(Suit.CLUBS, Rank.FIVE)
        );
        assertEquals(FULL_HOUSE, evaluator.evaluate(cards));
    }

    // --- Four of a Kind ---
    @Test
    void fourOfAKind() {
        List<Card> cards = List.of(
            c(Suit.HEARTS, Rank.ACE), c(Suit.DIAMONDS, Rank.ACE),
            c(Suit.CLUBS, Rank.ACE), c(Suit.SPADES, Rank.ACE),
            c(Suit.HEARTS, Rank.TWO), c(Suit.DIAMONDS, Rank.FIVE),
            c(Suit.CLUBS, Rank.SEVEN)
        );
        assertEquals(FOUR_OF_A_KIND, evaluator.evaluate(cards));
    }

    // --- Wheel Straight Flush ---
    @Test
    void wheelStraightFlush() {
        // A♣ 2♣ 3♣ 4♣ 5♣ K♦ Q♦ → best 5-card hand is A-2-3-4-5 suited = STRAIGHT_FLUSH
        List<Card> cards = List.of(
            c(Suit.CLUBS, Rank.ACE), c(Suit.CLUBS, Rank.TWO),
            c(Suit.CLUBS, Rank.THREE), c(Suit.CLUBS, Rank.FOUR),
            c(Suit.CLUBS, Rank.FIVE), c(Suit.DIAMONDS, Rank.KING),
            c(Suit.DIAMONDS, Rank.QUEEN)
        );
        assertEquals(STRAIGHT_FLUSH, evaluator.evaluate(cards));
    }

    // --- Straight Flush ---
    @Test
    void straightFlush() {
        List<Card> cards = List.of(
            c(Suit.CLUBS, Rank.FIVE), c(Suit.CLUBS, Rank.SIX),
            c(Suit.CLUBS, Rank.SEVEN), c(Suit.CLUBS, Rank.EIGHT),
            c(Suit.CLUBS, Rank.NINE), c(Suit.DIAMONDS, Rank.ACE),
            c(Suit.HEARTS, Rank.KING)
        );
        assertEquals(STRAIGHT_FLUSH, evaluator.evaluate(cards));
    }

    // --- Royal Flush ---
    @Test
    void royalFlush() {
        List<Card> cards = List.of(
            c(Suit.SPADES, Rank.TEN), c(Suit.SPADES, Rank.JACK),
            c(Suit.SPADES, Rank.QUEEN), c(Suit.SPADES, Rank.KING),
            c(Suit.SPADES, Rank.ACE), c(Suit.HEARTS, Rank.TWO),
            c(Suit.DIAMONDS, Rank.THREE)
        );
        assertEquals(ROYAL_FLUSH, evaluator.evaluate(cards));
    }

    // --- Compare ---
    @Test
    void flushBeatsStreet() {
        List<Card> flushHand = List.of(
            c(Suit.HEARTS, Rank.TWO), c(Suit.HEARTS, Rank.FIVE),
            c(Suit.HEARTS, Rank.SEVEN), c(Suit.HEARTS, Rank.NINE),
            c(Suit.HEARTS, Rank.JACK), c(Suit.DIAMONDS, Rank.THREE),
            c(Suit.CLUBS, Rank.FOUR)
        );
        List<Card> straightHand = List.of(
            c(Suit.HEARTS, Rank.FIVE), c(Suit.DIAMONDS, Rank.SIX),
            c(Suit.CLUBS, Rank.SEVEN), c(Suit.SPADES, Rank.EIGHT),
            c(Suit.HEARTS, Rank.NINE), c(Suit.DIAMONDS, Rank.TWO),
            c(Suit.CLUBS, Rank.KING)
        );
        assertTrue(evaluator.compare(flushHand, straightHand) > 0);
    }

    @Test
    void equalHandsReturnZero() {
        List<Card> hand1 = List.of(
            c(Suit.HEARTS, Rank.ACE), c(Suit.DIAMONDS, Rank.ACE),
            c(Suit.CLUBS, Rank.TWO), c(Suit.SPADES, Rank.FIVE),
            c(Suit.HEARTS, Rank.SEVEN), c(Suit.DIAMONDS, Rank.NINE),
            c(Suit.CLUBS, Rank.JACK)
        );
        List<Card> hand2 = List.of(
            c(Suit.SPADES, Rank.ACE), c(Suit.CLUBS, Rank.ACE),
            c(Suit.HEARTS, Rank.TWO), c(Suit.DIAMONDS, Rank.FIVE),
            c(Suit.SPADES, Rank.SEVEN), c(Suit.CLUBS, Rank.NINE),
            c(Suit.HEARTS, Rank.JACK)
        );
        assertEquals(0, evaluator.compare(hand1, hand2));
    }
}
