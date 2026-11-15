package bg.deck.belot.model.request;

import bg.deck.belot.engine.Card;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The card a player puts on the table.
 *
 * <p>No seat and no trick number: which seat is whoever the token says, and
 * which trick is whichever one is open. A request that could name either could
 * name somebody else's.
 *
 * @param card     the card, which must be one the sender was dealt and has not played
 * @param announce with the player's first card: the key of the announcement
 *                 they chose, from the ones they were offered. Absent takes the
 *                 richest
 * @param belote   with a card that would say a belote: false to play it without
 *                 saying it. Absent says it
 */
public record BelotPlayRequest(@NotNull Card card, @Size(max = 200) String announce, Boolean belote) {

    /** Just a card: the richest announcement, and any belote said. */
    public BelotPlayRequest(Card card) {
        this(card, null, null);
    }
}
