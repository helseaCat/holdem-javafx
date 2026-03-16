package com.nekocatgato.engine;

import com.nekocatgato.model.Card;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HandEvaluator {

    public enum HandRank {
        HIGH_CARD, ONE_PAIR, TWO_PAIR, THREE_OF_A_KIND,
        STRAIGHT, FLUSH, FULL_HOUSE, FOUR_OF_A_KIND,
        STRAIGHT_FLUSH, ROYAL_FLUSH
    }

    /**
     * Evaluates the best 5-card hand from the given cards (must be >= 5).
     * Returns the highest HandRank achievable across all C(n,5) combinations.
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

        HandRank best = HandRank.HIGH_CARD;
        for (List<Card> five : combinations(cards, 5)) {
            HandRank rank = classifyFiveCard(five);
            if (rank.ordinal() > best.ordinal()) {
                best = rank;
            }
        }
        return best;
    }

    /**
     * Compares two hands. Returns positive if hand1 wins, negative if hand2 wins, 0 for tie.
     */
    public int compare(List<Card> hand1, List<Card> hand2) {
        return evaluate(hand1).ordinal() - evaluate(hand2).ordinal();
    }

    /** Generates all C(n,k) subsets of the given list. */
    private List<List<Card>> combinations(List<Card> cards, int k) {
        List<List<Card>> result = new ArrayList<>();
        combine(cards, k, 0, new ArrayList<>(), result);
        return result;
    }

    private void combine(List<Card> cards, int k, int start,
                         List<Card> current, List<List<Card>> result) {
        if (current.size() == k) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < cards.size(); i++) {
            current.add(cards.get(i));
            combine(cards, k, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }

    /** Classifies a 5-card hand into the best HandRank it represents. */
    HandRank classifyFiveCard(List<Card> five) {
        Map<Integer, Long> freq = rankFrequencies(five);
        long maxFreq = freq.values().stream().mapToLong(Long::longValue).max().orElse(0);
        long pairs = freq.values().stream().filter(v -> v == 2).count();

        boolean flush = isFlush(five);
        List<Integer> sortedRanks = five.stream()
                .map(c -> c.getRank().ordinal())
                .sorted()
                .collect(Collectors.toList());
        boolean straight = isStraight(sortedRanks);

        if (flush && straight) {
            // Royal flush: TEN(8), JACK(9), QUEEN(10), KING(11), ACE(12)
            if (sortedRanks.equals(List.of(8, 9, 10, 11, 12))) return HandRank.ROYAL_FLUSH;
            return HandRank.STRAIGHT_FLUSH;
        }
        if (maxFreq == 4) return HandRank.FOUR_OF_A_KIND;
        if (maxFreq == 3 && pairs == 1) return HandRank.FULL_HOUSE;
        if (flush) return HandRank.FLUSH;
        if (straight) return HandRank.STRAIGHT;
        if (maxFreq == 3) return HandRank.THREE_OF_A_KIND;
        if (pairs == 2) return HandRank.TWO_PAIR;
        if (pairs == 1) return HandRank.ONE_PAIR;

        return HandRank.HIGH_CARD;
    }

    /** Returns true if all 5 cards share the same suit. */
    private boolean isFlush(List<Card> five) {
        Card.Suit suit = five.get(0).getSuit();
        return five.stream().allMatch(c -> c.getSuit() == suit);
    }

    /**
     * Returns true if the 5 sorted rank ordinals form a straight.
     * Handles the wheel (A-2-3-4-5) explicitly: [0,1,2,3,12].
     */
    private boolean isStraight(List<Integer> sortedRanks) {
        // Wheel: A-2-3-4-5
        if (sortedRanks.equals(List.of(0, 1, 2, 3, 12))) return true;
        // General: 5 consecutive ordinals
        for (int i = 1; i < sortedRanks.size(); i++) {
            if (sortedRanks.get(i) != sortedRanks.get(i - 1) + 1) return false;
        }
        return true;
    }

    /** Returns a map of rank ordinal → count for the given 5-card hand. */
    private Map<Integer, Long> rankFrequencies(List<Card> five) {
        return five.stream()
                .collect(Collectors.groupingBy(c -> c.getRank().ordinal(), Collectors.counting()));
    }
}
