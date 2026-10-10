package bg.deck.belot.enums;

/**
 * Whether the deal is being played for double or quadruple.
 *
 * <p>Contra doubles the deal, recontra by the contracting side quadruples it,
 * and both apply to everything the deal is worth — bonuses included, and points
 * left hanging stay doubled on their way to the next deal. RULES §5 and §8.
 */
public enum Doubling {

    NONE(1),
    CONTRA(2),
    RECONTRA(4);

    private final int multiplier;

    Doubling(int multiplier) {
        this.multiplier = multiplier;
    }

    public int multiplier() {
        return multiplier;
    }
}
