package bg.deck.belot.engine;

import bg.deck.belot.enums.Seat;

/**
 * One card, from one seat.
 *
 * @param seat who played it
 * @param card what they played
 */
public record Play(Seat seat, Card card) {
}
