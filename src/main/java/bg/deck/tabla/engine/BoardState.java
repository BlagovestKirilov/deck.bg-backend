package bg.deck.tabla.engine;

import bg.deck.tabla.enums.Side;

import java.util.Arrays;

/**
 * An immutable backgammon position.
 *
 * <p>{@code points} has length 25; index 0 is unused so that the array index
 * equals the point number. A positive value is that many WHITE checkers on the
 * point, a negative value is that many BLACK checkers, zero is empty.
 *
 * <p>Numbering is canonical (WHITE's frame) everywhere in this type. Direction
 * is handled exclusively by {@link MoverView}, so nothing here needs to know
 * whose turn it is.
 */
public final class BoardState {

    public static final int POINTS = 24;
    public static final int CHECKERS_PER_SIDE = 15;

    private final int[] points;
    private final int whiteBar;
    private final int blackBar;
    private final int whiteOff;
    private final int blackOff;

    public BoardState(int[] points, int whiteBar, int blackBar, int whiteOff, int blackOff) {
        if (points.length != POINTS + 1) {
            throw new IllegalArgumentException("points must have length " + (POINTS + 1));
        }
        this.points = points.clone();
        this.whiteBar = whiteBar;
        this.blackBar = blackBar;
        this.whiteOff = whiteOff;
        this.blackOff = blackOff;
    }

    /** The standard opening position. It is its own mirror under {@code 25 - i}. */
    public static BoardState initial() {
        int[] p = new int[POINTS + 1];
        p[1] = -2;
        p[6] = 5;
        p[8] = 3;
        p[12] = -5;
        p[13] = 5;
        p[17] = -3;
        p[19] = -5;
        p[24] = 2;
        return new BoardState(p, 0, 0, 0, 0);
    }

    public int at(int point) {
        return points[point];
    }

    /** Defensive copy; callers must not mutate the board in place. */
    public int[] points() {
        return points.clone();
    }

    public int bar(Side side) {
        return side == Side.WHITE ? whiteBar : blackBar;
    }

    public int off(Side side) {
        return side == Side.WHITE ? whiteOff : blackOff;
    }

    public int whiteBar() {
        return whiteBar;
    }

    public int blackBar() {
        return blackBar;
    }

    public int whiteOff() {
        return whiteOff;
    }

    public int blackOff() {
        return blackOff;
    }

    /** Returns a copy with the raw fields replaced. Used by the rules engine only. */
    BoardState with(int[] newPoints, int newWhiteBar, int newBlackBar, int newWhiteOff, int newBlackOff) {
        return new BoardState(newPoints, newWhiteBar, newBlackBar, newWhiteOff, newBlackOff);
    }

    /**
     * How far this side still has to travel, in pips. A player who bears off
     * every checker has a pip count of zero.
     */
    public int pipCount(Side side) {
        int pips = 0;
        for (int i = 1; i <= POINTS; i++) {
            int n = points[i];
            if (side == Side.WHITE && n > 0) {
                pips += n * i;
            } else if (side == Side.BLACK && n < 0) {
                pips += -n * (25 - i);
            }
        }
        // A checker on the bar re-enters at the far end: 25 pips from home.
        pips += bar(side) * 25;
        return pips;
    }

    /**
     * Total checkers accounted for by this side. Always 15 in a valid position —
     * the invariant the fuzz test asserts after every hop.
     */
    public int checkerCount(Side side) {
        int total = bar(side) + off(side);
        for (int i = 1; i <= POINTS; i++) {
            int n = points[i];
            if (side == Side.WHITE && n > 0) {
                total += n;
            } else if (side == Side.BLACK && n < 0) {
                total += -n;
            }
        }
        return total;
    }

    /** Compact, greppable encoding used as a memo key and as the persisted column. */
    public String encode() {
        StringBuilder sb = new StringBuilder(96);
        for (int i = 1; i <= POINTS; i++) {
            if (i > 1) {
                sb.append(',');
            }
            sb.append(points[i]);
        }
        sb.append('|').append(whiteBar).append(',').append(blackBar)
          .append('|').append(whiteOff).append(',').append(blackOff);
        return sb.toString();
    }

    public static BoardState decode(String encoded) {
        String[] parts = encoded.split("\\|");
        if (parts.length != 3) {
            throw new IllegalArgumentException("malformed board: " + encoded);
        }
        String[] pts = parts[0].split(",");
        if (pts.length != POINTS) {
            throw new IllegalArgumentException("malformed board: " + encoded);
        }
        int[] p = new int[POINTS + 1];
        for (int i = 0; i < POINTS; i++) {
            p[i + 1] = Integer.parseInt(pts[i]);
        }
        String[] bars = parts[1].split(",");
        String[] offs = parts[2].split(",");
        return new BoardState(p, Integer.parseInt(bars[0]), Integer.parseInt(bars[1]),
                Integer.parseInt(offs[0]), Integer.parseInt(offs[1]));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BoardState other)) {
            return false;
        }
        return whiteBar == other.whiteBar && blackBar == other.blackBar
                && whiteOff == other.whiteOff && blackOff == other.blackOff
                && Arrays.equals(points, other.points);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(points) + whiteBar + 7 * blackBar + 13 * whiteOff + 17 * blackOff;
    }

    @Override
    public String toString() {
        return "BoardState[" + encode() + "]";
    }
}
