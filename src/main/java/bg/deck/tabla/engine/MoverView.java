package bg.deck.tabla.engine;

import bg.deck.tabla.enums.Side;

/**
 * The board seen from the side that is about to move — the single most important
 * abstraction in this engine.
 *
 * <p>Indices run 1..24 for points plus {@link #BAR} (25) for the bar, always
 * counting <em>distance from the mover's off tray</em>. In this frame the mover
 * always travels from a higher index to a lower one, which collapses three
 * separate rules into one arithmetic expression:
 *
 * <ul>
 *   <li>ordinary move — {@code to = from - die}</li>
 *   <li>enter from the bar — {@code to = 25 - die}, i.e. {@code from - die} with {@code from = BAR}</li>
 *   <li>bear off — {@code to = from - die <= 0}</li>
 * </ul>
 *
 * <p>Writing the rules twice, once per direction, is where backgammon
 * implementations go wrong. This type exists so they are written once.
 */
public final class MoverView {

    /** Pseudo-point representing the mover's bar. */
    public static final int BAR = 25;
    /** Pseudo-point representing the mover's off tray. */
    public static final int OFF = 0;
    /** The mover's home board is 1..6 in this frame. */
    public static final int HOME_HIGH = 6;

    private final BoardState board;
    private final Side side;

    private MoverView(BoardState board, Side side) {
        this.board = board;
        this.side = side;
    }

    public static MoverView of(BoardState board, Side side) {
        return new MoverView(board, side);
    }

    public Side side() {
        return side;
    }

    public BoardState board() {
        return board;
    }

    /** Canonical point number for a normalised index (1..24). */
    public int toCanonical(int n) {
        return side == Side.WHITE ? n : 25 - n;
    }

    /**
     * Checkers at normalised index {@code n}: positive = the mover's own,
     * negative = the opponent's. {@code at(BAR)} is the mover's bar count.
     */
    public int at(int n) {
        if (n == BAR) {
            return board.bar(side);
        }
        int raw = board.at(toCanonical(n));
        return side == Side.WHITE ? raw : -raw;
    }

    public int bar() {
        return board.bar(side);
    }

    public int opponentBar() {
        return board.bar(side.opponent());
    }

    public int off() {
        return board.off(side);
    }

    /**
     * True when every checker this side still has in play sits in the home board
     * (1..6) and none is on the bar.
     *
     * <p>Note the condition is "all <em>remaining</em> checkers", not "all 15" —
     * borne-off checkers no longer count. The bar is re-checked every time, so a
     * mid-turn hit correctly revokes the right to bear off.
     */
    public boolean allHome() {
        if (bar() > 0) {
            return false;
        }
        for (int p = HOME_HIGH + 1; p <= BoardState.POINTS; p++) {
            if (at(p) > 0) {
                return false;
            }
        }
        return true;
    }

    /** Highest occupied point in the mover's home board, or 0 when home is empty. */
    public int highestOccupiedHomePoint() {
        for (int p = HOME_HIGH; p >= 1; p--) {
            if (at(p) > 0) {
                return p;
            }
        }
        return 0;
    }
}
