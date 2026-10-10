package bg.deck.belot.engine;

import bg.deck.belot.enums.DeclarationKind;
import bg.deck.belot.enums.Rank;
import bg.deck.belot.enums.Suit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Something a hand holds and says out loud on its first trick.
 *
 * @param kind    what it is
 * @param suit    the suit it is in — a sequence's or a belote's; null for a carré
 * @param topRank the highest card of a sequence, or the rank of a carré
 * @param points  what it is worth
 */
public record Declaration(DeclarationKind kind, Suit suit, Rank topRank, int points) {

    /** Sequences are measured by length first, then by their top card. */
    public int length() {
        return switch (kind) {
            case TERZ -> 3;
            case QUARTE -> 4;
            case QUINTE -> 5;
            default -> 0;
        };
    }

    public boolean isSequence() {
        return length() > 0;
    }

    /**
     * The cards this is made of.
     *
     * <p>Needed because a card may not be counted twice: "ако една и съща
     * карта участва едновременно в каре и поредица, играчът избира кое от
     * двете да обяви". A declaration that knows its own cards is what lets
     * {@code Declarations} see the overlap.
     */
    public List<Card> cards() {
        return switch (kind) {
            case TERZ, QUARTE, QUINTE -> run();
            case CARRE -> Arrays.stream(Suit.values()).map(each -> new Card(each, topRank)).toList();
            case BELOTE -> List.of(new Card(suit, Rank.KING), new Card(suit, Rank.QUEEN));
        };
    }

    /** A sequence runs down from its top card, in the natural order. */
    private List<Card> run() {
        Rank[] ranks = Rank.values();
        int top = topRank.naturalOrder();
        return IntStream.rangeClosed(top - length() + 1, top)
                .mapToObj(order -> new Card(suit, ranks[order]))
                .toList();
    }
}
