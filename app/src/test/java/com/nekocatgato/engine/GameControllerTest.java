package com.nekocatgato.engine;

import com.nekocatgato.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}