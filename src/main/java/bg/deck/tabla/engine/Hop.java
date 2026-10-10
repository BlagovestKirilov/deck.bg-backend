package bg.deck.tabla.engine;

/**
 * One checker movement consuming one die, expressed in the mover's normalised
 * frame (see {@link MoverView}).
 *
 * @param from  1..24, or {@link MoverView#BAR} when entering from the bar
 * @param to    1..24, or {@link MoverView#OFF} when bearing off
 * @param die   the die value consumed, 1..6
 * @param hit   true when this lands on a lone opponent checker, sending it to the bar
 */
public record Hop(int from, int to, int die, boolean hit) {

    public boolean isEntry() {
        return from == MoverView.BAR;
    }

    public boolean isBearOff() {
        return to == MoverView.OFF;
    }

    @Override
    public String toString() {
        String f = isEntry() ? "bar" : String.valueOf(from);
        String t = isBearOff() ? "off" : String.valueOf(to);
        return f + "/" + t + "(" + die + ")" + (hit ? "*" : "");
    }

    /** Compact encoding for the persisted pending-hops column. */
    public String encode() {
        return from + ":" + to + ":" + die + ":" + (hit ? 1 : 0);
    }

    public static Hop decode(String s) {
        String[] p = s.split(":");
        if (p.length != 4) {
            throw new IllegalArgumentException("malformed hop: " + s);
        }
        return new Hop(Integer.parseInt(p[0]), Integer.parseInt(p[1]),
                Integer.parseInt(p[2]), "1".equals(p[3]));
    }
}
