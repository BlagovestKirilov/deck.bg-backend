package bg.deck.belot.enums;

/**
 * What a deal is played in, in bidding order — each one beats the ones above
 * it. Passing is not a contract; the bidding holds that.
 *
 * <p>Whether a suit counts as trump is the only thing the rest of the engine
 * asks: a suit contract trumps one suit, all trumps trumps everything, no
 * trumps nothing. RULES §5.
 */
public enum Contract {

    CLUBS(Suit.CLUBS),
    DIAMONDS(Suit.DIAMONDS),
    HEARTS(Suit.HEARTS),
    SPADES(Suit.SPADES),
    NO_TRUMPS(null),
    ALL_TRUMPS(null);

    private final Suit trumpSuit;

    Contract(Suit trumpSuit) {
        this.trumpSuit = trumpSuit;
    }

    /** The one suit that trumps, if this contract has one. */
    public java.util.Optional<Suit> trumpSuit() {
        return java.util.Optional.ofNullable(trumpSuit);
    }

    /** True when a card of this suit is played by the trump order and points. */
    public boolean isTrump(Suit suit) {
        return switch (this) {
            case ALL_TRUMPS -> true;
            case NO_TRUMPS -> false;
            default -> trumpSuit == suit;
        };
    }

    /** True when this contract outbids {@code other}. */
    public boolean beats(Contract other) {
        return ordinal() > other.ordinal();
    }
}
