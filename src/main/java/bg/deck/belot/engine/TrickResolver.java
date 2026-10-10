package bg.deck.belot.engine;

import bg.deck.belot.enums.Contract;
import bg.deck.belot.enums.Seat;
import bg.deck.belot.enums.Suit;
import java.util.Comparator;
import java.util.Optional;

/**
 * Who is winning a trick, and who took it.
 *
 * <p>Three ways a card can stand, worst first: it followed neither the led suit
 * nor the trump suit and cannot win at all; it followed the led suit; it is a
 * trump. Within one of those, the higher card by the contract's own order wins.
 *
 * <p>All trumps and no trumps have no separate trump suit — every suit is read
 * by the same order — so there only the led suit can win a trick. A heart on a
 * led spade loses in all trumps exactly as it does in no trumps, however high
 * it is.
 */
public final class TrickResolver {

    private TrickResolver() {
    }

    /** Who holds the trick as it stands, or empty while nothing is played. */
    public static Optional<Seat> leader(Trick trick, Contract contract) {
        return winning(trick, contract).map(Play::seat);
    }

    /** The play that holds the trick as it stands. */
    public static Optional<Play> winning(Trick trick, Contract contract) {
        return trick.ledSuit().flatMap(led -> trick.plays().stream()
                .max(Comparator.comparingInt((Play play) -> standing(play.card(), led, contract))
                        .thenComparingInt(play -> strength(play.card(), contract))));
    }

    /** True when {@code card} would take the trick as it stands. */
    public static boolean beatsAll(Card card, Trick trick, Contract contract) {
        Suit led = trick.ledSuit().orElse(card.suit());
        return winning(trick, contract)
                .map(best -> standing(card, led, contract) > standing(best.card(), led, contract)
                        || (standing(card, led, contract) == standing(best.card(), led, contract)
                            && strength(card, contract) > strength(best.card(), contract)))
                .orElse(true);
    }

    private static int standing(Card card, Suit led, Contract contract) {
        if (contract.trumpSuit().map(trump -> trump == card.suit()).orElse(false)) {
            return 2;
        }
        return card.suit() == led ? 1 : 0;
    }

    private static int strength(Card card, Contract contract) {
        return card.rank().strength(contract.isTrump(card.suit()));
    }
}
