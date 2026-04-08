package com.nekocatgato.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GameTableView.computeActionConfig().
 * Validates: Requirements 1.1, 2.1, 2.2, 3.1, 3.2, 3.3, 4.1, 4.2, 4.3, 6.1, 6.2, 6.3
 */
class GameTableViewActionConfigTest {

    /** Req 6.1: callAmount=0, plenty of chips → Fold+Check+Raise, Call hidden */
    @Test
    void noCallWithSufficientChips() {
        var config = GameTableView.computeActionConfig(0, 500);
        assertTrue(config.foldEnabled());
        assertTrue(config.checkVisible());
        assertFalse(config.callVisible());
        assertTrue(config.raiseEnabled());
        assertTrue(config.raiseInputEnabled());
        assertTrue(config.allInEnabled());
    }

    /** Req 6.2: callAmount>0, plenty of chips → Fold+Call+Raise, Check hidden */
    @Test
    void callWithSufficientChips() {
        var config = GameTableView.computeActionConfig(50, 500);
        assertTrue(config.foldEnabled());
        assertFalse(config.checkVisible());
        assertTrue(config.callVisible());
        assertEquals("Call $50", config.callText());
        assertTrue(config.raiseEnabled());
        assertTrue(config.raiseInputEnabled());
        assertTrue(config.allInEnabled());
    }

    /** Req 6.3: callAmount>0, chips below BIG_BLIND → Fold+Call, Check hidden, Raise disabled */
    @Test
    void callWithInsufficientChips() {
        var config = GameTableView.computeActionConfig(50, 10);
        assertTrue(config.foldEnabled());
        assertFalse(config.checkVisible());
        assertTrue(config.callVisible());
        assertFalse(config.raiseEnabled());
        assertFalse(config.raiseInputEnabled());
        assertFalse(config.allInEnabled());
    }

    /** Edge case: callAmount=0, chips below BIG_BLIND → Fold+Check, Call hidden, Raise disabled */
    @Test
    void noCallWithInsufficientChips() {
        var config = GameTableView.computeActionConfig(0, 10);
        assertTrue(config.foldEnabled());
        assertTrue(config.checkVisible());
        assertFalse(config.callVisible());
        assertFalse(config.raiseEnabled());
        assertFalse(config.raiseInputEnabled());
        assertFalse(config.allInEnabled());
    }

    /** Defensive: negative callAmount treated as 0 */
    @Test
    void negativeCallAmountTreatedAsZero() {
        var config = GameTableView.computeActionConfig(-10, 500);
        assertTrue(config.checkVisible());
        assertFalse(config.callVisible());
    }

    /** Defensive: negative playerChips disables raise */
    @Test
    void negativePlayerChipsDisablesRaise() {
        var config = GameTableView.computeActionConfig(0, -5);
        assertTrue(config.foldEnabled());
        assertFalse(config.raiseEnabled());
        assertFalse(config.raiseInputEnabled());
        assertFalse(config.allInEnabled());
    }
}
