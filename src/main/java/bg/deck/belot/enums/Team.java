package bg.deck.belot.enums;

/** The two partnerships. Partners sit opposite, so the seats pair up N/S and W/E. */
public enum Team {
    NORTH_SOUTH, EAST_WEST;

    public static Team of(Seat seat) {
        return seat == Seat.NORTH || seat == Seat.SOUTH ? NORTH_SOUTH : EAST_WEST;
    }

    public Team opponent() {
        return this == NORTH_SOUTH ? EAST_WEST : NORTH_SOUTH;
    }
}
