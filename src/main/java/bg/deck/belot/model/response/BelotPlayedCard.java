package bg.deck.belot.model.response;

import bg.deck.belot.engine.Card;
import bg.deck.belot.enums.Seat;

/**
 * A card on the table, and who put it there.
 *
 * @param seat who played it
 * @param card what they played
 */
public record BelotPlayedCard(Seat seat, Card card) {
}
