package com.nekocatgato.engine;

import com.nekocatgato.model.GameState;
import com.nekocatgato.model.Player;

/**
 * Callback interface for receiving game engine events on the UI thread.
 * The GameController dispatches these via Platform.runLater so implementations
 * can safely update JavaFX nodes.
 */
public interface GameEventListener {
    void onPhaseChanged(GameState.Phase phase, GameState state);
    void onPlayerTurn(Player player, int callAmount);
    void onPlayerActed(Player player, Player.Action action);
    void onRoundComplete(GameState state);
    void onPlayerEliminated(Player player);
    void onGameOver(Player winner);
    void onError(Exception e);
}
