package bg.deck.belot.model.response;

import bg.deck.belot.engine.Seat;

import java.util.List;

/**
 * A finished trick, and who took it.
 *
 * @param dealNumber which hand it was the last trick of
 * @param cards      the four cards, in the order they were played
 * @param wonBy      who took it
 */
public record BelotTrickView(int dealNumber, List<BelotPlayedCard> cards, Seat wonBy) {

    public BelotTrickView {
        cards = List.copyOf(cards);
    }
}
