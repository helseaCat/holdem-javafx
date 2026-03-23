package com.nekocatgato.engine;

import com.nekocatgato.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameControllerTest {
    private GameController controller;
    private List<Player> players;

    @BeforeEach
    void setUp() {
        controller = new GameController();
        HumanPlayer human = new HumanPlayer("Player", 1000);
        AIPlayer ai = new AIPlayer("AI", 1000);
        players = List.of(human, ai);
    }

    @Test
    void controllerInitializes() {
        assertNotNull(controller);
        assertNotNull(players);
        assertEquals(2, players.size());
    }

    @Test
    void startGameWithNullListThrows() {
        assertThrows(IllegalArgumentException.class, () -> controller.startGame(null));
    }

    @Test
    void startGameWithEmptyListThrows() {
        assertThrows(IllegalArgumentException.class, () -> controller.startGame(List.of()));
    }

    @Test
    void startGameWithOnePlayerThrows() {
        List<Player> singlePlayer = List.of(new HumanPlayer("Solo", 1000));
        assertThrows(IllegalArgumentException.class, () -> controller.startGame(singlePlayer));
    }

    @Test
    void startGameWithNullElementThrows() {
        List<Player> withNull = new ArrayList<>();
        withNull.add(new HumanPlayer("Player", 1000));
        withNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> controller.startGame(withNull));
    }

    @Test
    void startGameWithZeroChipsThrows() {
        List<Player> zeroChips = List.of(
            new HumanPlayer("Player", 1000),
            new AIPlayer("Broke", 0)
        );
        assertThrows(IllegalArgumentException.class, () -> controller.startGame(zeroChips));
    }

    @Test
    void startGameWithNegativeChipsThrows() {
        List<Player> negativeChips = List.of(
            new HumanPlayer("Player", 1000),
            new AIPlayer("InDebt", -100)
        );
        assertThrows(IllegalArgumentException.class, () -> controller.startGame(negativeChips));
    }
}