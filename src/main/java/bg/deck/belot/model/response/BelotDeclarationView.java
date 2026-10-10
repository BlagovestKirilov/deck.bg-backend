package bg.deck.belot.model.response;

import bg.deck.belot.engine.Declaration;
import bg.deck.belot.enums.DeclarationKind;
import bg.deck.belot.enums.Rank;
import bg.deck.belot.enums.Seat;
import bg.deck.belot.enums.Suit;

/**
 * Something a player holds that is worth announcing: a sequence, four of a
 * kind, or the king and queen of trumps.
 *
 * <p>Sent once the first trick is complete, which is when they are announced
 * at a table. Before that it would be telling three people what is in
 * somebody's hand.
 *
 * <p>While the hand is being played only the kind is filled in, because only
 * the kind is said out loud. A player announces "терца" and the table learns
 * that much; which suit it is in, what it runs up to and therefore whose
 * counts are settled when the hand is over and the cards are on the table.
 * Sending them sooner would hand three people a card each.
 *
 * @param seat    who holds it
 * @param kind    a sequence of three, four or five, a carré, or belote
 * @param suit    the suit it is in — null until the hand is over
 * @param topRank the card it runs up to, which is what settles ties — null until then
 * @param points  what it is worth, before the other side's is compared to it; 0 until then
 */
public record BelotDeclarationView(Seat seat, DeclarationKind kind, Suit suit, Rank topRank, int points) {

    /** All of it: for a hand that has been played out and counted. */
    public static BelotDeclarationView of(Seat seat, Declaration declaration) {
        return new BelotDeclarationView(
                seat, declaration.kind(), declaration.suit(), declaration.topRank(), declaration.points());
    }

    /** What was said out loud, and nothing more: for a hand still being played. */
    public static BelotDeclarationView called(Seat seat, Declaration declaration) {
        return new BelotDeclarationView(seat, declaration.kind(), null, null, 0);
    }
}
