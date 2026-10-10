package bg.deck.belot.engine;

import bg.deck.belot.enums.Contract;
import java.util.Collection;

/**
 * What cards are worth in a given contract.
 *
 * <p>The ninety for capot is the deal's, not a card's, so the scorer adds it.
 * RULES §3.
 *
 * <p>A no-trump deal counts double. The cards themselves add to 130 there, and
 * a no-trump deal is worth 260 — the doubling is what accounts for the whole of
 * the difference, the last trick included.
 */
public final class CardPoints {

    /** The last trick, to whoever takes it, before any doubling. */
    public static final int LAST_TRICK = 10;
    /** Every trick to one team. */
    public static final int CAPOT = 90;

    private CardPoints() {
    }

    /** Two in a no-trump deal, one in any other. */
    public static int multiplier(Contract contract) {
        return contract == Contract.NO_TRUMPS ? 2 : 1;
    }

    public static int of(Card card, Contract contract) {
        return card.rank().points(contract.isTrump(card.suit())) * multiplier(contract);
    }

    public static int of(Collection<Card> cards, Contract contract) {
        return cards.stream().mapToInt(card -> of(card, contract)).sum();
    }

    /** The last trick, as this contract counts it. */
    public static int lastTrick(Contract contract) {
        return LAST_TRICK * multiplier(contract);
    }

    /** Every card in the deck, plus the last trick: what one deal is worth. */
    public static int dealTotal(Contract contract) {
        return of(Deck.full(), contract) + lastTrick(contract);
    }
}
