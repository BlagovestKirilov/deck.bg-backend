package bg.deck.tabla.enums;

/**
 * The two sides of a backgammon board.
 *
 * <p>Point numbering is fixed in WHITE's frame: points 1..24, WHITE moves
 * 24 -> 1 and bears off past 1, BLACK moves 1 -> 24 and bears off past 24.
 * WHITE's home board is 1..6, BLACK's is 19..24.
 *
 * <p>{@code Game.firstPlayer} is always WHITE, so no side column is persisted.
 */
public enum Side {
    WHITE,
    BLACK;

    public Side opponent() {
        return this == WHITE ? BLACK : WHITE;
    }
}
