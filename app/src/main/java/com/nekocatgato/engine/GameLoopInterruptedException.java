package com.nekocatgato.engine;

/**
 * Unchecked exception thrown when the engine thread is interrupted
 * while waiting for a player action during the game loop.
 */
public class GameLoopInterruptedException extends RuntimeException {

    public GameLoopInterruptedException(InterruptedException cause) {
        super("Game loop interrupted", cause);
    }
}
