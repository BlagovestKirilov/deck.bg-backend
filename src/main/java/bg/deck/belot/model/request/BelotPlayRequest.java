package bg.deck.belot.model.request;

import bg.deck.belot.engine.Card;
import jakarta.validation.constraints.NotNull;

/**
 * The card a player puts on the table.
 *
 * <p>No seat and no trick number: which seat is whoever the token says, and
 * which trick is whichever one is open. A request that could name either could
 * name somebody else's.
 *
 * @param card the card, which must be one the sender was dealt and has not played
 */
public record BelotPlayRequest(@NotNull Card card) {
}
