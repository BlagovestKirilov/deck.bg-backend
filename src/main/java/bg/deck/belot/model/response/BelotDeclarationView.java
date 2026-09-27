package bg.deck.belot.model.response;

import bg.deck.belot.engine.Declaration;
import bg.deck.belot.engine.DeclarationKind;
import bg.deck.belot.engine.Rank;
import bg.deck.belot.engine.Seat;
import bg.deck.belot.engine.Suit;

/**
 * Something a player holds that is worth announcing: a sequence, four of a
 * kind, or the king and queen of trumps.
 *
 * <p>Sent once the first trick is complete, which is when they are announced
 * at a table. Before that it would be telling three people what is in
 * somebody's hand.
 *
 * @param seat    who holds it
 * @param kind    a sequence of three, four or five, a carré, or belote
 * @param suit    the suit it is in
 * @param topRank the card it runs up to, which is what settles ties
 * @param points  what it is worth, before the other side's is compared to it
 */
public record BelotDeclarationView(Seat seat, DeclarationKind kind, Suit suit, Rank topRank, int points) {

    public static BelotDeclarationView of(Seat seat, Declaration declaration) {
        return new BelotDeclarationView(
                seat, declaration.kind(), declaration.suit(), declaration.topRank(), declaration.points());
    }
}
