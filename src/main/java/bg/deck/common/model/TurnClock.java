package bg.deck.common.model;

import bg.deck.common.service.GameInactivityService;

import java.time.Instant;
import bg.deck.santase.model.GameState;
import bg.deck.tabla.model.TablaGameState;

/**
 * The part of a game's state the turn timer cares about.
 *
 * <p>Implemented by both {@link GameState} (Santase) and {@link TablaGameState},
 * so {@link GameInactivityService} and the
 * inactivity-surrender path work for both games without branching on game type.
 */
public interface TurnClock {

    /**
     * Santase's budget for one turn: 20s to act, then a 10s "still there?"
     * warning, plus 3s of slack so the client always reaches the warning before
     * this deadline does.
     */
    int TURN_SECONDS = 33;

    /**
     * Табла's budget: 45s to act plus the same 10s warning and 3s of slack. A
     * turn here is several taps (roll, move each die, confirm), not one card, so
     * Santase's 20s is too short.
     */
    int TABLA_TURN_SECONDS = 58;

    /** The budget this game gives a player for one turn. */
    default int turnSeconds() {
        return TURN_SECONDS;
    }

    Player getInTurnPlayer();

    /** Hands the turn to {@code player} and restarts the clock. */
    void setInTurnPlayer(Player player);

    Instant getNextMoveTime();

    /** Pushes the deadline out by a fresh {@link #TURN_SECONDS}. */
    void extendNextMoveTime();

    boolean isInTurn(Player player);
}
