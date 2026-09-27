package bg.deck.belot.model.response;

import bg.deck.belot.engine.Card;
import bg.deck.belot.engine.Contract;
import bg.deck.belot.engine.Seat;

import java.util.List;

/**
 * The hand being played, as one seat sees it.
 *
 * <p>Everything here is on the table and public, except {@code yours} — the
 * cards this player may legally play right now, which is empty when it is not
 * their turn.
 *
 * @param contract what was bid, and what the cards are worth under it
 * @param declarer who bid it
 * @param toAct    whose turn it is to play
 * @param trickNo  which trick, counted from one
 * @param onTable  the cards on the table, in the order they were played — the
 *                 trick in progress, or the one just finished until somebody
 *                 leads the next
 * @param wonBy    who took the trick on the table, once it is complete
 * @param yours    what the player being sent this may play now
 */
public record BelotPlayView(
        Contract contract,
        Seat declarer,
        Seat toAct,
        int trickNo,
        List<BelotPlayedCard> onTable,
        Seat wonBy,
        List<Card> yours
) {

    public BelotPlayView {
        onTable = List.copyOf(onTable);
        yours = List.copyOf(yours);
    }
}
