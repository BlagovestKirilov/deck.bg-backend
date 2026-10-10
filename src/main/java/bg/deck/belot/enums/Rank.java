package bg.deck.belot.enums;

/**
 * A rank, with both of the orders and both of the point tables belot uses.
 *
 * <p>Everything about a card that depends on whether its suit is trump lives
 * here, in one table that can be read against the rules page: the trump order
 * J 9 A 10 K Q 8 7, the plain order A 10 K Q J 9 8 7, and the points each
 * carries in either case. See {@code docs/belot/RULES.md} §2 and §3.
 *
 * @see CardPoints
 */
public enum Rank {

    SEVEN("7", 1, 1, 0, 0),
    EIGHT("8", 2, 2, 0, 0),
    NINE("9", 3, 7, 0, 14),
    TEN("10", 7, 5, 10, 10),
    JACK("J", 4, 8, 2, 20),
    QUEEN("Q", 5, 3, 3, 3),
    KING("K", 6, 4, 4, 4),
    ACE("A", 8, 6, 11, 11);

    private final String symbol;
    private final int plainStrength;
    private final int trumpStrength;
    private final int plainPoints;
    private final int trumpPoints;

    Rank(String symbol, int plainStrength, int trumpStrength, int plainPoints, int trumpPoints) {
        this.symbol = symbol;
        this.plainStrength = plainStrength;
        this.trumpStrength = trumpStrength;
        this.plainPoints = plainPoints;
        this.trumpPoints = trumpPoints;
    }

    public String symbol() {
        return symbol;
    }

    /** Higher beats lower, within one suit. */
    public int strength(boolean trump) {
        return trump ? trumpStrength : plainStrength;
    }

    public int points(boolean trump) {
        return trump ? trumpPoints : plainPoints;
    }

    /**
     * Where this rank sits in the natural order — 7 8 9 10 J Q K A — which is
     * the one sequences are built from, whatever the contract. The enum is
     * declared in that order, and {@code BelotRulesTableTest} holds it to it.
     */
    public int naturalOrder() {
        return ordinal();
    }
}
