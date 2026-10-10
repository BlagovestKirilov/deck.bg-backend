package bg.deck.belot.engine;

import bg.deck.belot.enums.Rank;
import bg.deck.belot.enums.Suit;
import java.util.ArrayList;
import java.util.List;

/** The 32 cards, in a fixed order. Shuffling belongs to the deal, not here. */
public final class Deck {

    private Deck() {
    }

    public static List<Card> full() {
        List<Card> cards = new ArrayList<>(32);
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(suit, rank));
            }
        }
        return List.copyOf(cards);
    }
}
