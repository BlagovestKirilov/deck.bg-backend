package bg.deck.belot.enums;

/**
 * The kinds of declaration a hand can hold. RULES §7.
 *
 * <p>A carré is worth different points depending on its rank, so it carries no
 * fixed value here; the rest do.
 */
public enum DeclarationKind {

    /** Three in sequence, 20. */
    TERZ(20),
    /** Four in sequence, 50. */
    QUARTE(50),
    /** Five or more in sequence, 100. */
    QUINTE(100),
    /** Four of a kind: 200 jacks, 150 nines, 100 for ten, queen, king or ace. */
    CARRE(0),
    /** King and queen of a trump suit, 20. */
    BELOTE(20);

    private final int points;

    DeclarationKind(int points) {
        this.points = points;
    }

    public int points() {
        return points;
    }
}
