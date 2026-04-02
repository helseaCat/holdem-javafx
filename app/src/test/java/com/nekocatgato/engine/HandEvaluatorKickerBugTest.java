package com.nekocatgato.engine;

import com.nekocatgato.model.Card;
import com.nekocatgato.model.Card.Rank;
import com.nekocatgato.model.Card.Suit;
import net.jqwik.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug condition exploration test for kicker tie-breaking.
 *
 * Property 1: Same-rank hands with different kickers must return nonzero from compare().
 *
 * On UNFIXED code these tests FAIL — that confirms the bug exists.
 * After the fix, these tests PASS — that confirms the bug is resolved.
 */
class HandEvaluatorKickerBugTest {

    private final HandEvaluator evaluator = new HandEvaluator();

    private Card c(Suit s, Rank r) { return new Card(s, r); }

    // ── Deterministic scoped examples ──────────────────────────────────

    /** Pair of Kings vs Pair of Fives — Kings should win. */
    @Example
    void pairOfKings_vs_pairOfFives() {
        List<Card> pairKings = List.of(
            c(Suit.HEARTS, Rank.KING), c(Suit.DIAMONDS, Rank.KING),
            c(Suit.CLUBS, Rank.NINE), c(Suit.SPADES, Rank.SEVEN),
            c(Suit.HEARTS, Rank.THREE), c(Suit.DIAMONDS, Rank.TWO),
            c(Suit.CLUBS, Rank.FOUR)
        );
        List<Card> pairFives = List.of(
            c(Suit.HEARTS, Rank.FIVE), c(Suit.DIAMONDS, Rank.FIVE),
            c(Suit.CLUBS, Rank.ACE), c(Suit.SPADES, Rank.QUEEN),
            c(Suit.HEARTS, Rank.JACK), c(Suit.DIAMONDS, Rank.TWO),
            c(Suit.SPADES, Rank.THREE)
        );
        // Both evaluate to ONE_PAIR
        assertEquals(evaluator.evaluate(pairKings), evaluator.evaluate(pairFives),
            "Precondition: both hands should be ONE_PAIR");
        assertTrue(evaluator.compare(pairKings, pairFives) > 0,
            "Pair of Kings should beat Pair of Fives");
    }

    /** Pair of Aces + King kicker vs Pair of Aces + Ten kicker — King kicker wins. */
    @Example
    void pairAces_kingKicker_vs_pairAces_tenKicker() {
        List<Card> acesKingKicker = List.of(
            c(Suit.HEARTS, Rank.ACE), c(Suit.DIAMONDS, Rank.ACE),
            c(Suit.CLUBS, Rank.KING), c(Suit.SPADES, Rank.EIGHT),
            c(Suit.HEARTS, Rank.FOUR), c(Suit.DIAMONDS, Rank.TWO),
            c(Suit.CLUBS, Rank.THREE)
        );
        List<Card> acesTenKicker = List.of(
            c(Suit.SPADES, Rank.ACE), c(Suit.CLUBS, Rank.ACE),
            c(Suit.HEARTS, Rank.TEN), c(Suit.DIAMONDS, Rank.SEVEN),
            c(Suit.SPADES, Rank.FIVE), c(Suit.CLUBS, Rank.TWO),
            c(Suit.HEARTS, Rank.THREE)
        );
        assertEquals(evaluator.evaluate(acesKingKicker), evaluator.evaluate(acesTenKicker),
            "Precondition: both hands should be ONE_PAIR");
        assertTrue(evaluator.compare(acesKingKicker, acesTenKicker) > 0,
            "Pair of Aces with King kicker should beat Pair of Aces with Ten kicker");
    }

    /** 9-high straight vs 6-high straight — 9-high wins. */
    @Example
    void nineHighStraight_vs_sixHighStraight() {
        List<Card> nineHigh = List.of(
            c(Suit.HEARTS, Rank.FIVE), c(Suit.DIAMONDS, Rank.SIX),
            c(Suit.CLUBS, Rank.SEVEN), c(Suit.SPADES, Rank.EIGHT),
            c(Suit.HEARTS, Rank.NINE), c(Suit.DIAMONDS, Rank.TWO),
            c(Suit.CLUBS, Rank.THREE)
        );
        List<Card> sixHigh = List.of(
            c(Suit.SPADES, Rank.TWO), c(Suit.CLUBS, Rank.THREE),
            c(Suit.HEARTS, Rank.FOUR), c(Suit.DIAMONDS, Rank.FIVE),
            c(Suit.SPADES, Rank.SIX), c(Suit.CLUBS, Rank.KING),
            c(Suit.HEARTS, Rank.QUEEN)
        );
        assertEquals(evaluator.evaluate(nineHigh), evaluator.evaluate(sixHigh),
            "Precondition: both hands should be STRAIGHT");
        assertTrue(evaluator.compare(nineHigh, sixHigh) > 0,
            "9-high straight should beat 6-high straight");
    }

    /** Kings-full vs Queens-full — Kings-full wins. */
    @Example
    void kingsFull_vs_queensFull() {
        List<Card> kingsFull = List.of(
            c(Suit.HEARTS, Rank.KING), c(Suit.DIAMONDS, Rank.KING),
            c(Suit.CLUBS, Rank.KING), c(Suit.SPADES, Rank.FIVE),
            c(Suit.HEARTS, Rank.FIVE), c(Suit.DIAMONDS, Rank.TWO),
            c(Suit.CLUBS, Rank.THREE)
        );
        List<Card> queensFull = List.of(
            c(Suit.HEARTS, Rank.QUEEN), c(Suit.DIAMONDS, Rank.QUEEN),
            c(Suit.CLUBS, Rank.QUEEN), c(Suit.SPADES, Rank.ACE),
            c(Suit.HEARTS, Rank.ACE), c(Suit.DIAMONDS, Rank.TWO),
            c(Suit.SPADES, Rank.THREE)
        );
        assertEquals(evaluator.evaluate(kingsFull), evaluator.evaluate(queensFull),
            "Precondition: both hands should be FULL_HOUSE");
        assertTrue(evaluator.compare(kingsFull, queensFull) > 0,
            "Kings-full should beat Queens-full");
    }

    // ── Property-based test ────────────────────────────────────────────

    /**
     * For any two 7-card hands that share the same HandRank but have different
     * kicker compositions, compare() must return nonzero.
     *
     * We generate same-rank hand pairs by picking a rank category and building
     * two hands that both classify to that rank but with different values.
     */
    @Property(tries = 200)
    void sameRankDifferentKickers_compareReturnsNonzero(
            @ForAll("sameRankHandPairs") List<List<Card>> pair) {
        List<Card> hand1 = pair.get(0);
        List<Card> hand2 = pair.get(1);

        // Precondition: both hands evaluate to the same rank
        HandEvaluator.HandRank rank1 = evaluator.evaluate(hand1);
        HandEvaluator.HandRank rank2 = evaluator.evaluate(hand2);
        assertEquals(rank1, rank2, "Precondition: hands must share the same HandRank");

        // Bug condition: compare() should return nonzero when kickers differ
        int result = evaluator.compare(hand1, hand2);
        assertNotEquals(0, result,
            "compare() returned 0 for same-rank hands with different kickers: "
            + rank1 + " — hand1: " + hand1 + " vs hand2: " + hand2);
    }

    @Provide
    Arbitrary<List<List<Card>>> sameRankHandPairs() {
        return Arbitraries.of(
            "ONE_PAIR_DIFF_PAIR",
            "ONE_PAIR_DIFF_KICKER",
            "STRAIGHT_DIFF_HIGH",
            "FULL_HOUSE_DIFF_TRIPS"
        ).flatMap(this::generateHandPair);
    }

    private Arbitrary<List<List<Card>>> generateHandPair(String scenario) {
        return switch (scenario) {
            case "ONE_PAIR_DIFF_PAIR" -> pairVsPairDifferentRank();
            case "ONE_PAIR_DIFF_KICKER" -> pairVsPairDifferentKicker();
            case "STRAIGHT_DIFF_HIGH" -> straightVsStraightDifferentHigh();
            case "FULL_HOUSE_DIFF_TRIPS" -> fullHouseVsFullHouseDifferentTrips();
            default -> throw new IllegalArgumentException(scenario);
        };
    }

    /** Two ONE_PAIR hands with different pair ranks. */
    private Arbitrary<List<List<Card>>> pairVsPairDifferentRank() {
        Rank[] ranks = Rank.values();
        return Arbitraries.integers().between(0, ranks.length - 2).flatMap(i ->
            Arbitraries.integers().between(i + 1, ranks.length - 1).map(j -> {
                // i < j, so pair at ranks[j] is higher
                Rank lowPair = ranks[i];
                Rank highPair = ranks[j];
                // Pick 5 distinct filler ranks that avoid both pair ranks
                List<Rank> fillers = Arrays.stream(ranks)
                    .filter(r -> r != lowPair && r != highPair)
                    .limit(5)
                    .toList();
                List<Card> hand1 = buildPairHand(highPair, fillers.subList(0, 5));
                List<Card> hand2 = buildPairHand(lowPair, fillers.subList(0, 5));
                return List.of(hand1, hand2);
            })
        );
    }

    /** Two ONE_PAIR hands with the same pair rank but different first kicker. */
    private Arbitrary<List<List<Card>>> pairVsPairDifferentKicker() {
        return Arbitraries.of(Rank.values()).flatMap(pairRank -> {
            List<Rank> others = Arrays.stream(Rank.values())
                .filter(r -> r != pairRank)
                .toList();
            return Arbitraries.integers().between(0, others.size() - 2).flatMap(i ->
                Arbitraries.integers().between(i + 1, others.size() - 1).map(j -> {
                    // Build kicker sets that differ in the highest kicker
                    Rank lowKicker = others.get(i);
                    Rank highKicker = others.get(j);
                    List<Rank> remaining = others.stream()
                        .filter(r -> r != lowKicker && r != highKicker)
                        .limit(4)
                        .toList();
                    List<Card> hand1 = buildPairHandWithKickers(pairRank,
                        highKicker, remaining.get(0), remaining.get(1), remaining.get(2), remaining.get(3));
                    List<Card> hand2 = buildPairHandWithKickers(pairRank,
                        lowKicker, remaining.get(0), remaining.get(1), remaining.get(2), remaining.get(3));
                    return List.of(hand1, hand2);
                })
            );
        });
    }

    /** Two STRAIGHT hands with different high cards. */
    private Arbitrary<List<List<Card>>> straightVsStraightDifferentHigh() {
        // Straights: high card from FIVE(3) to ACE(12), but we need non-flush
        // Valid high cards for non-wheel: SIX(4) through ACE(12), wheel = FIVE(3)
        return Arbitraries.integers().between(4, 12).flatMap(high1 ->
            Arbitraries.integers().between(4, high1 - 1)
                .filter(high2 -> high2 >= 4)
                .map(high2 -> {
                    List<Card> hand1 = buildStraightHand(high1);
                    List<Card> hand2 = buildStraightHand(high2);
                    return List.of(hand1, hand2);
                })
        );
    }

    /** Two FULL_HOUSE hands with different trip ranks. */
    private Arbitrary<List<List<Card>>> fullHouseVsFullHouseDifferentTrips() {
        Rank[] ranks = Rank.values();
        return Arbitraries.integers().between(0, ranks.length - 2).flatMap(i ->
            Arbitraries.integers().between(i + 1, ranks.length - 1).map(j -> {
                Rank lowTrips = ranks[i];
                Rank highTrips = ranks[j];
                // Pick a pair rank different from both
                Rank pairRank = Arrays.stream(ranks)
                    .filter(r -> r != lowTrips && r != highTrips)
                    .findFirst().orElseThrow();
                List<Card> hand1 = buildFullHouseHand(highTrips, pairRank);
                List<Card> hand2 = buildFullHouseHand(lowTrips, pairRank);
                return List.of(hand1, hand2);
            })
        );
    }

    // ── Hand builders ──────────────────────────────────────────────────

    private List<Card> buildPairHand(Rank pairRank, List<Rank> fillers) {
        List<Card> hand = new ArrayList<>();
        hand.add(c(Suit.HEARTS, pairRank));
        hand.add(c(Suit.DIAMONDS, pairRank));
        Suit[] suits = { Suit.CLUBS, Suit.SPADES, Suit.HEARTS, Suit.DIAMONDS, Suit.CLUBS };
        for (int i = 0; i < 5; i++) {
            hand.add(c(suits[i], fillers.get(i)));
        }
        return hand;
    }

    private List<Card> buildPairHandWithKickers(Rank pairRank,
            Rank kicker1, Rank kicker2, Rank kicker3, Rank filler1, Rank filler2) {
        return List.of(
            c(Suit.HEARTS, pairRank), c(Suit.DIAMONDS, pairRank),
            c(Suit.CLUBS, kicker1), c(Suit.SPADES, kicker2),
            c(Suit.HEARTS, kicker3), c(Suit.DIAMONDS, filler1),
            c(Suit.CLUBS, filler2)
        );
    }

    private List<Card> buildStraightHand(int highOrdinal) {
        Rank[] ranks = Rank.values();
        // 5 consecutive ranks ending at highOrdinal, alternating suits to avoid flush
        Suit[] suits = { Suit.HEARTS, Suit.DIAMONDS, Suit.CLUBS, Suit.SPADES, Suit.HEARTS };
        List<Card> hand = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            hand.add(c(suits[i], ranks[highOrdinal - 4 + i]));
        }
        // 2 filler cards that don't extend the straight
        Rank filler1 = ranks[Math.max(0, highOrdinal - 6)];
        Rank filler2 = ranks[Math.max(0, highOrdinal - 7)];
        // Ensure fillers don't accidentally create a longer straight
        hand.add(c(Suit.DIAMONDS, filler1));
        hand.add(c(Suit.CLUBS, filler2));
        return hand;
    }

    private List<Card> buildFullHouseHand(Rank tripRank, Rank pairRank) {
        Rank filler1 = Arrays.stream(Rank.values())
            .filter(r -> r != tripRank && r != pairRank)
            .findFirst().orElseThrow();
        Rank filler2 = Arrays.stream(Rank.values())
            .filter(r -> r != tripRank && r != pairRank && r != filler1)
            .findFirst().orElseThrow();
        return List.of(
            c(Suit.HEARTS, tripRank), c(Suit.DIAMONDS, tripRank), c(Suit.CLUBS, tripRank),
            c(Suit.SPADES, pairRank), c(Suit.HEARTS, pairRank),
            c(Suit.DIAMONDS, filler1), c(Suit.CLUBS, filler2)
        );
    }
}
