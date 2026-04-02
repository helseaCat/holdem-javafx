package com.nekocatgato.engine;

import com.nekocatgato.model.Card;
import com.nekocatgato.model.Card.Rank;
import com.nekocatgato.model.Card.Suit;
import com.nekocatgato.engine.HandEvaluator.HandRank;
import net.jqwik.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Preservation property tests for kicker tie-breaking bugfix.
 *
 * Property 2: Different-rank and true-tie behavior must remain unchanged.
 *
 * These tests capture the OBSERVED behavior on UNFIXED code.
 * They must continue to PASS after the fix is applied (no regressions).
 */
class HandEvaluatorPreservationTest {

    private final HandEvaluator evaluator = new HandEvaluator();

    private Card c(Suit s, Rank r) { return new Card(s, r); }

    // ── Observed baseline deterministic tests ──────────────────────────

    /**
     * Observed: compare(flush, straight) returns positive (sign=+1).
     * Different HandRank comparison must be preserved.
     */
    @Example
    void differentRank_flushBeatsStraight() {
        List<Card> flush = List.of(
            c(Suit.HEARTS, Rank.ACE), c(Suit.HEARTS, Rank.KING),
            c(Suit.HEARTS, Rank.NINE), c(Suit.HEARTS, Rank.SEVEN),
            c(Suit.HEARTS, Rank.TWO), c(Suit.DIAMONDS, Rank.THREE),
            c(Suit.CLUBS, Rank.FOUR));
        List<Card> straight = List.of(
            c(Suit.HEARTS, Rank.FIVE), c(Suit.DIAMONDS, Rank.SIX),
            c(Suit.CLUBS, Rank.SEVEN), c(Suit.SPADES, Rank.EIGHT),
            c(Suit.HEARTS, Rank.NINE), c(Suit.DIAMONDS, Rank.TWO),
            c(Suit.CLUBS, Rank.THREE));

        int result = evaluator.compare(flush, straight);
        assertTrue(result > 0, "Flush should beat straight, got " + result);
    }

    /**
     * Observed: compare(pairAces_KingKicker, pairAces_KingKicker_diffSuits) returns 0.
     * True tie (identical rank values, different suits only) must be preserved.
     */
    @Example
    void trueTie_samePairSameKickers_returnsZero() {
        List<Card> pairAcesK1 = List.of(
            c(Suit.HEARTS, Rank.ACE), c(Suit.DIAMONDS, Rank.ACE),
            c(Suit.CLUBS, Rank.KING), c(Suit.SPADES, Rank.EIGHT),
            c(Suit.HEARTS, Rank.FOUR), c(Suit.DIAMONDS, Rank.TWO),
            c(Suit.CLUBS, Rank.THREE));
        List<Card> pairAcesK2 = List.of(
            c(Suit.SPADES, Rank.ACE), c(Suit.CLUBS, Rank.ACE),
            c(Suit.DIAMONDS, Rank.KING), c(Suit.HEARTS, Rank.EIGHT),
            c(Suit.SPADES, Rank.FOUR), c(Suit.CLUBS, Rank.TWO),
            c(Suit.DIAMONDS, Rank.THREE));

        int result = evaluator.compare(pairAcesK1, pairAcesK2);
        assertEquals(0, result, "True tie (same ranks, different suits) should return 0");
    }

    /**
     * Observed: evaluate(royalFlush) returns ROYAL_FLUSH.
     * Classification must be preserved.
     */
    @Example
    void evaluate_royalFlush_returnsCorrectRank() {
        List<Card> royal = List.of(
            c(Suit.HEARTS, Rank.TEN), c(Suit.HEARTS, Rank.JACK),
            c(Suit.HEARTS, Rank.QUEEN), c(Suit.HEARTS, Rank.KING),
            c(Suit.HEARTS, Rank.ACE), c(Suit.DIAMONDS, Rank.TWO),
            c(Suit.CLUBS, Rank.THREE));

        assertEquals(HandRank.ROYAL_FLUSH, evaluator.evaluate(royal));
    }

    /**
     * Observed: null input throws IllegalArgumentException.
     * Input validation must be preserved.
     */
    @Example
    void evaluate_null_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(null));
    }

    /**
     * Observed: too-few-cards input throws IllegalArgumentException.
     * Input validation must be preserved.
     */
    @Example
    void evaluate_tooFewCards_throwsIllegalArgument() {
        List<Card> twoCards = List.of(
            c(Suit.HEARTS, Rank.ACE), c(Suit.DIAMONDS, Rank.KING));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(twoCards));
    }

    // ── Property-based preservation tests ──────────────────────────────

    /**
     * Property: For any two 7-card hands with different HandRank,
     * sign(compare(h1, h2)) == sign(evaluate(h1).ordinal() - evaluate(h2).ordinal()).
     *
     * This preserves the existing rank-ordinal comparison for different-rank hands.
     */
    @Property(tries = 500)
    void differentRankHands_compareMatchesOrdinalDifference(
            @ForAll("differentRankHandPairs") List<List<Card>> pair) {
        List<Card> hand1 = pair.get(0);
        List<Card> hand2 = pair.get(1);

        HandRank rank1 = evaluator.evaluate(hand1);
        HandRank rank2 = evaluator.evaluate(hand2);

        // Precondition: ranks must differ
        Assume.that(!rank1.equals(rank2));

        int compareResult = evaluator.compare(hand1, hand2);
        int ordinalDiff = rank1.ordinal() - rank2.ordinal();

        assertEquals(Integer.signum(ordinalDiff), Integer.signum(compareResult),
            "Different-rank comparison sign must match ordinal difference sign. "
            + rank1 + " vs " + rank2);
    }

    /**
     * Property: For any two 7-card hands that are identical in rank values
     * (only suits differ), compare(h1, h2) == 0.
     *
     * This preserves true-tie behavior.
     */
    @Property(tries = 500)
    void identicalRankValues_differentSuits_compareReturnsZero(
            @ForAll("trueTieHandPairs") List<List<Card>> pair) {
        List<Card> hand1 = pair.get(0);
        List<Card> hand2 = pair.get(1);

        int result = evaluator.compare(hand1, hand2);
        assertEquals(0, result,
            "Hands with identical rank values (different suits) must tie. "
            + "hand1: " + hand1 + " hand2: " + hand2);
    }

    /**
     * Property: For any valid 7-card hand, evaluate() returns a non-null HandRank.
     *
     * This preserves classification correctness.
     */
    @Property(tries = 500)
    void anyValidHand_evaluateReturnsNonNull(
            @ForAll("validSevenCardHand") List<Card> hand) {
        HandRank rank = evaluator.evaluate(hand);
        assertNotNull(rank, "evaluate() must return a non-null HandRank");
    }

    // ── Providers ──────────────────────────────────────────────────────

    /**
     * Generates pairs of 7-card hands that have different HandRanks.
     * Strategy: build one hand of a known high rank and one of a known low rank.
     */
    @Provide
    Arbitrary<List<List<Card>>> differentRankHandPairs() {
        return Arbitraries.of(
            "FLUSH_VS_STRAIGHT",
            "FULL_HOUSE_VS_TWO_PAIR",
            "THREE_KIND_VS_ONE_PAIR",
            "ONE_PAIR_VS_HIGH_CARD"
        ).flatMap(this::buildDifferentRankPair);
    }

    private Arbitrary<List<List<Card>>> buildDifferentRankPair(String scenario) {
        return switch (scenario) {
            case "FLUSH_VS_STRAIGHT" -> Arbitraries.just(List.of(
                buildFlushHand(), buildStraightHand()));
            case "FULL_HOUSE_VS_TWO_PAIR" -> Arbitraries.just(List.of(
                buildFullHouseHand(), buildTwoPairHand()));
            case "THREE_KIND_VS_ONE_PAIR" -> Arbitraries.just(List.of(
                buildThreeOfAKindHand(), buildOnePairHand()));
            case "ONE_PAIR_VS_HIGH_CARD" -> Arbitraries.just(List.of(
                buildOnePairHand(), buildHighCardHand()));
            default -> throw new IllegalArgumentException(scenario);
        };
    }

    /**
     * Generates pairs of 7-card hands with identical rank values but different suits.
     * We pick 7 distinct ranks and assign different suit permutations.
     */
    @Provide
    Arbitrary<List<List<Card>>> trueTieHandPairs() {
        Suit[] suitsA = { Suit.HEARTS, Suit.DIAMONDS, Suit.CLUBS, Suit.SPADES,
                          Suit.HEARTS, Suit.DIAMONDS, Suit.CLUBS };
        Suit[] suitsB = { Suit.SPADES, Suit.CLUBS, Suit.DIAMONDS, Suit.HEARTS,
                          Suit.SPADES, Suit.CLUBS, Suit.DIAMONDS };

        return Arbitraries.shuffle(Rank.values()).map(shuffled -> {
            List<Rank> ranks = shuffled.subList(0, 7);
            List<Card> hand1 = new ArrayList<>();
            List<Card> hand2 = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                hand1.add(c(suitsA[i], ranks.get(i)));
                hand2.add(c(suitsB[i], ranks.get(i)));
            }
            return List.of(hand1, hand2);
        });
    }

    /**
     * Generates a valid 7-card hand with 7 distinct cards from a standard deck.
     */
    @Provide
    Arbitrary<List<Card>> validSevenCardHand() {
        List<Card> fullDeck = new ArrayList<>();
        for (Suit s : Suit.values()) {
            for (Rank r : Rank.values()) {
                fullDeck.add(c(s, r));
            }
        }
        return Arbitraries.shuffle(fullDeck).map(deck -> deck.subList(0, 7));
    }

    // ── Hand builders (deterministic, for different-rank pairs) ────────

    private List<Card> buildFlushHand() {
        return List.of(
            c(Suit.HEARTS, Rank.ACE), c(Suit.HEARTS, Rank.KING),
            c(Suit.HEARTS, Rank.NINE), c(Suit.HEARTS, Rank.SEVEN),
            c(Suit.HEARTS, Rank.TWO), c(Suit.DIAMONDS, Rank.THREE),
            c(Suit.CLUBS, Rank.FOUR));
    }

    private List<Card> buildStraightHand() {
        return List.of(
            c(Suit.HEARTS, Rank.FIVE), c(Suit.DIAMONDS, Rank.SIX),
            c(Suit.CLUBS, Rank.SEVEN), c(Suit.SPADES, Rank.EIGHT),
            c(Suit.HEARTS, Rank.NINE), c(Suit.DIAMONDS, Rank.TWO),
            c(Suit.CLUBS, Rank.THREE));
    }

    private List<Card> buildFullHouseHand() {
        return List.of(
            c(Suit.HEARTS, Rank.KING), c(Suit.DIAMONDS, Rank.KING),
            c(Suit.CLUBS, Rank.KING), c(Suit.SPADES, Rank.FIVE),
            c(Suit.HEARTS, Rank.FIVE), c(Suit.DIAMONDS, Rank.TWO),
            c(Suit.CLUBS, Rank.THREE));
    }

    private List<Card> buildTwoPairHand() {
        return List.of(
            c(Suit.HEARTS, Rank.JACK), c(Suit.DIAMONDS, Rank.JACK),
            c(Suit.CLUBS, Rank.SEVEN), c(Suit.SPADES, Rank.SEVEN),
            c(Suit.HEARTS, Rank.THREE), c(Suit.DIAMONDS, Rank.TWO),
            c(Suit.CLUBS, Rank.NINE));
    }

    private List<Card> buildThreeOfAKindHand() {
        return List.of(
            c(Suit.HEARTS, Rank.QUEEN), c(Suit.DIAMONDS, Rank.QUEEN),
            c(Suit.CLUBS, Rank.QUEEN), c(Suit.SPADES, Rank.EIGHT),
            c(Suit.HEARTS, Rank.FOUR), c(Suit.DIAMONDS, Rank.TWO),
            c(Suit.CLUBS, Rank.SIX));
    }

    private List<Card> buildOnePairHand() {
        return List.of(
            c(Suit.HEARTS, Rank.TEN), c(Suit.DIAMONDS, Rank.TEN),
            c(Suit.CLUBS, Rank.ACE), c(Suit.SPADES, Rank.EIGHT),
            c(Suit.HEARTS, Rank.FOUR), c(Suit.DIAMONDS, Rank.TWO),
            c(Suit.CLUBS, Rank.SIX));
    }

    private List<Card> buildHighCardHand() {
        return List.of(
            c(Suit.HEARTS, Rank.ACE), c(Suit.DIAMONDS, Rank.KING),
            c(Suit.CLUBS, Rank.NINE), c(Suit.SPADES, Rank.SEVEN),
            c(Suit.HEARTS, Rank.FOUR), c(Suit.DIAMONDS, Rank.TWO),
            c(Suit.CLUBS, Rank.SIX));
    }
}
