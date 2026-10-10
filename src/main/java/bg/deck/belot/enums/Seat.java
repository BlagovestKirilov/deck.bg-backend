package bg.deck.belot.enums;

/**
 * The four places at the table, in the order play moves — counter-clockwise,
 * as RULES §1 has it.
 *
 * <p>Partners sit opposite each other, so a seat's partner is always two along.
 */
public enum Seat {
    NORTH, WEST, SOUTH, EAST;

    /** The seat that plays after this one. */
    public Seat next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** The seat across the table. */
    public Seat partner() {
        return values()[(ordinal() + 2) % values().length];
    }

    public boolean isPartnerOf(Seat other) {
        return partner() == other;
    }

    /** True when the two seats are on opposing teams. */
    public boolean isOpponentOf(Seat other) {
        return this != other && !isPartnerOf(other);
    }
}
