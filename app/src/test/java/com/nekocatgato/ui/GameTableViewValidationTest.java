package com.nekocatgato.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for GameTableView.validateRaiseAmount().
 * Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5
 */
class GameTableViewValidationTest {

    @Test
    void exactValidAmount() {
        assertEquals(100, GameTableView.validateRaiseAmount("100", 500));
    }

    @Test
    void capsToPlayerChips() {
        assertEquals(300, GameTableView.validateRaiseAmount("500", 300));
    }

    @Test
    void belowBigBlindReturnsNegativeOne() {
        assertEquals(-1, GameTableView.validateRaiseAmount("10", 500));
    }

    @Test
    void emptyInputReturnsNegativeOne() {
        assertEquals(-1, GameTableView.validateRaiseAmount("", 500));
    }

    @Test
    void nonNumericReturnsNegativeOne() {
        assertEquals(-1, GameTableView.validateRaiseAmount("abc", 500));
    }

    @Test
    void exactBoundaryBigBlindEqualsChips() {
        assertEquals(20, GameTableView.validateRaiseAmount("20", 20));
    }
}
