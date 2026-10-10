package bg.deck.common.model;

import java.time.Instant;

/**
 * The part of a game's state a turn timer reads: when the turn now running
 * runs out.
 *
 * <p>Time only, no seat: each game's state implements it with its own seats,
 * and each game's turn timer reads the deadline through it the same way.
 */
public interface TurnClock {

    /** The budget this game gives a player for one turn, in seconds. */
    int turnSeconds();

    Instant getNextMoveTime();

    /**
     * When the turn now running began: a whole budget before its deadline.
     * After a "Continue" that is the moment it was pressed, since that gives
     * a fresh budget. Null when there is no deadline.
     */
    default Instant turnStartedAt() {
        return getNextMoveTime() == null ? null : getNextMoveTime().minusSeconds(turnSeconds());
    }

    /** Pushes the deadline out by a fresh budget. */
    void extendNextMoveTime();
}
