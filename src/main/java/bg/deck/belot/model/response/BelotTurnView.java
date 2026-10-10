package bg.deck.belot.model.response;

import bg.deck.belot.enums.Seat;

import java.time.Instant;

/**
 * Whose turn it is, and when it runs out.
 *
 * <p>A moment, not a number of seconds: a client counting down from a duration
 * the server sent drifts by however long the message took to arrive, and by
 * however long the tab was asleep. A client counting towards a timestamp does
 * not.
 *
 * <p>When it passes, the table acts for that seat — a pass while the bidding
 * is on, the first legal card once it is not. Nobody forfeits for being slow.
 *
 * @param seat      who the table is waiting for
 * @param startedAt when it started waiting — with the deadline, how much of
 *                  the turn is gone, which is what a draining bar shows
 * @param deadline  when it stops waiting
 */
public record BelotTurnView(Seat seat, Instant startedAt, Instant deadline) {
}
