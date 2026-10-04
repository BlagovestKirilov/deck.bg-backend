package bg.deck.tabla.engine;

import java.util.List;

/**
 * One checker played with several dice in a single tap.
 *
 * <p>The board still records ordinary {@link Hop}s — this only describes the run
 * so the client can offer the far destination directly instead of making the
 * player aim the same checker two, three or four times.
 *
 * <p>Two dice is the common case; doubles give four, and a checker may spend
 * any number of them. Each {@code via} must itself be a legal landing square, so
 * a combo is never a way around a blocked midpoint.
 *
 * @param vias the intermediate points, in order; one fewer than {@code dice}
 * @param dice the dice spent, in the order they are played
 */
public record ComboHop(int from, int to, List<Integer> vias, List<Integer> dice) {

    public ComboHop {
        vias = List.copyOf(vias);
        dice = List.copyOf(dice);
        if (dice.size() < 2 || vias.size() != dice.size() - 1) {
            throw new IllegalArgumentException(
                    "a combo is at least two dice with one via between each: "
                            + dice.size() + " dice, " + vias.size() + " vias");
        }
    }

    /** Every point the checker occupies in turn, the start and the end included. */
    public List<Integer> path() {
        return java.util.stream.Stream.concat(
                java.util.stream.Stream.concat(java.util.stream.Stream.of(from), vias.stream()),
                java.util.stream.Stream.of(to)).toList();
    }
}
