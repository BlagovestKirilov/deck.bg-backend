package bg.deck.belot.engine;

import bg.deck.belot.enums.Seat;
import java.util.List;

/** Picks a card for a seat from the ones it is allowed to play. */
@FunctionalInterface
public interface CardChooser {

    Card choose(Seat seat, List<Card> legal, Trick trick);
}
