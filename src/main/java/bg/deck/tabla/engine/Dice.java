package bg.deck.tabla.engine;

import java.util.Arrays;

/**
 * A roll of two dice.
 *
 * <p>{@link #values()} is the multiset of moves the roll grants: two entries
 * normally, four for doubles. Expressing doubles this way means the
 * "must use as many dice as possible" rule covers them with no extra logic.
 */
public record Dice(int d1, int d2) {

    public Dice {
        if (d1 < 1 || d1 > 6 || d2 < 1 || d2 > 6) {
            throw new IllegalArgumentException("die out of range: " + d1 + "," + d2);
        }
    }

    public boolean isDouble() {
        return d1 == d2;
    }

    public int[] values() {
        return isDouble() ? new int[]{d1, d1, d1, d1} : new int[]{d1, d2};
    }

    public int higher() {
        return Math.max(d1, d2);
    }

    public int lower() {
        return Math.min(d1, d2);
    }

    /** Removes one occurrence of {@code die}; throws when it is not present. */
    public static int[] without(int[] dice, int die) {
        for (int i = 0; i < dice.length; i++) {
            if (dice[i] == die) {
                int[] out = new int[dice.length - 1];
                System.arraycopy(dice, 0, out, 0, i);
                System.arraycopy(dice, i + 1, out, i, dice.length - i - 1);
                return out;
            }
        }
        throw new IllegalArgumentException("die " + die + " not in " + Arrays.toString(dice));
    }

    /** Distinct values in the multiset, so a doubles search branches once, not four times. */
    public static int[] distinct(int[] dice) {
        return Arrays.stream(dice).distinct().sorted().toArray();
    }

    public static String encode(int[] dice) {
        return Arrays.stream(dice).mapToObj(Integer::toString).reduce((a, b) -> a + "," + b).orElse("");
    }

    public static int[] decode(String s) {
        if (s == null || s.isBlank()) {
            return new int[0];
        }
        return Arrays.stream(s.split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray();
    }
}
