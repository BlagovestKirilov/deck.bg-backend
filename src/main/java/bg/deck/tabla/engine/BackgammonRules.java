package bg.deck.tabla.engine;

import bg.deck.tabla.enums.GameResultKind;
import bg.deck.tabla.enums.Side;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The rules of Обикновена табла (standard backgammon / portes), with no
 * doubling cube.
 *
 * <p>Deliberately free of Spring, JPA and any project type: it takes a
 * {@link BoardState} and returns a {@link BoardState}, so the whole rule set is
 * unit-testable in milliseconds.
 *
 * <p>The two rules implementations usually get wrong — "use both dice if you
 * legally can" and "use the higher die if you can only play one" — are not
 * encoded as per-move heuristics. They fall out of a full-turn search
 * ({@link #maxUsed}) plus an extendability check on every partial move
 * ({@link #legalTurnHops}).
 */
public final class BackgammonRules {

    private BackgammonRules() {
    }

    /* ------------------------------------------------------------------
       Single-die move generation
       ------------------------------------------------------------------ */

    /**
     * Every legal hop for one die from this position.
     *
     * <p>Entering from the bar takes absolute priority: while a checker is on
     * the bar, nothing else may move. That is the first branch, so no
     * sequence-level special case is needed anywhere else.
     */
    public static List<Hop> legalHops(MoverView v, int die) {
        if (v.bar() > 0) {
            return hopsFrom(v, MoverView.BAR, die);
        }
        List<Hop> out = new ArrayList<>(8);
        // Descending, so the output order is stable for the client.
        for (int p = BoardState.POINTS; p >= 1; p--) {
            if (v.at(p) > 0) {
                out.addAll(hopsFrom(v, p, die));
            }
        }
        return out;
    }

    private static List<Hop> hopsFrom(MoverView v, int from, int die) {
        int to = from - die;

        if (to >= 1) {
            // Blocked by two or more enemy checkers.
            if (v.at(to) < -1) {
                return List.of();
            }
            return List.of(new Hop(from, to, die, v.at(to) == -1));
        }

        // to <= 0 means bearing off. (from == BAR can never reach here: entering
        // with die d lands on 25 - d, which is at least 19.)
        if (!v.allHome()) {
            return List.of();
        }
        if (to == 0) {
            return List.of(new Hop(from, MoverView.OFF, die, false));
        }
        // Overshoot: legal only when no checker sits on a higher point.
        for (int q = from + 1; q <= MoverView.HOME_HIGH; q++) {
            if (v.at(q) > 0) {
                return List.of();
            }
        }
        return List.of(new Hop(from, MoverView.OFF, die, false));
    }

    /* ------------------------------------------------------------------
       Applying a hop
       ------------------------------------------------------------------ */

    /** Applies a hop, returning a new board. Assumes the hop is legal. */
    public static BoardState apply(BoardState board, Side side, Hop hop) {
        MoverView v = MoverView.of(board, side);
        int[] pts = board.points();
        int whiteBar = board.whiteBar();
        int blackBar = board.blackBar();
        int whiteOff = board.whiteOff();
        int blackOff = board.blackOff();
        int sign = side == Side.WHITE ? 1 : -1;

        // Remove the checker from its origin.
        if (hop.isEntry()) {
            if (side == Side.WHITE) {
                whiteBar--;
            } else {
                blackBar--;
            }
        } else {
            pts[v.toCanonical(hop.from())] -= sign;
        }

        // Place it, or bear it off.
        if (hop.isBearOff()) {
            if (side == Side.WHITE) {
                whiteOff++;
            } else {
                blackOff++;
            }
        } else {
            int dest = v.toCanonical(hop.to());
            if (hop.hit()) {
                // A lone enemy checker goes to the bar.
                pts[dest] = 0;
                if (side == Side.WHITE) {
                    blackBar++;
                } else {
                    whiteBar++;
                }
            }
            pts[dest] += sign;
        }

        return board.with(pts, whiteBar, blackBar, whiteOff, blackOff);
    }

    /* ------------------------------------------------------------------
       Full-turn search
       ------------------------------------------------------------------ */

    /**
     * The greatest number of dice that can be consumed from this position.
     *
     * <p>This single number enforces both "use both dice when you legally can"
     * and the doubles case: a turn may only be confirmed once exactly this many
     * dice have been played.
     */
    public static int maxUsed(BoardState board, Side side, int[] remainingDice) {
        return maxUsed(board, side, remainingDice, new HashMap<>());
    }

    private static int maxUsed(BoardState board, Side side, int[] dice, Map<String, Integer> memo) {
        if (dice.length == 0) {
            return 0;
        }
        int[] sorted = dice.clone();
        Arrays.sort(sorted);
        String key = board.encode() + '#' + Dice.encode(sorted);
        Integer cached = memo.get(key);
        if (cached != null) {
            return cached;
        }

        MoverView v = MoverView.of(board, side);
        int best = 0;
        // Branch on distinct values only: [3,3,3,3] explores one child, not four.
        for (int die : Dice.distinct(dice)) {
            for (Hop hop : legalHops(v, die)) {
                int used = 1 + maxUsed(apply(board, side, hop), side, Dice.without(dice, die), memo);
                if (used > best) {
                    best = used;
                }
                if (best == dice.length) {
                    break;
                }
            }
            if (best == dice.length) {
                break;
            }
        }

        memo.put(key, best);
        return best;
    }

    /**
     * The hops a player may legally choose right now, mid-turn.
     *
     * <p>This is the correctness core. A hop is offered only when playing it
     * still leaves a path to consuming {@code maxDiceUsable} dice in total —
     * without that check a player could greedily strand themselves on one die
     * when two were playable, which is precisely the classic bug.
     *
     * @param usedSoFar     dice already consumed this turn
     * @param maxDiceUsable {@code M}, computed once at roll time
     */
    public static List<Hop> legalTurnHops(BoardState board, Side side, int[] remainingDice,
                                          int usedSoFar, int maxDiceUsable) {
        if (remainingDice.length == 0 || usedSoFar >= maxDiceUsable) {
            return List.of();
        }
        MoverView v = MoverView.of(board, side);
        List<Hop> out = new ArrayList<>(16);

        for (int die : Dice.distinct(remainingDice)) {
            for (Hop hop : legalHops(v, die)) {
                int after = 1 + maxUsed(apply(board, side, hop), side, Dice.without(remainingDice, die));
                if (usedSoFar + after == maxDiceUsable) {
                    out.add(hop);
                }
            }
        }

        // "Use the higher die when only one can be played" is a genuinely separate
        // rule, not implied by maximality. It applies only on the first hop of a
        // turn where exactly one die is playable and the two dice differ.
        if (usedSoFar == 0 && maxDiceUsable == 1 && remainingDice.length == 2
                && remainingDice[0] != remainingDice[1]) {
            int higher = Math.max(remainingDice[0], remainingDice[1]);
            boolean higherPlayable = out.stream().anyMatch(h -> h.die() == higher);
            if (higherPlayable) {
                out.removeIf(h -> h.die() != higher);
            }
        }

        return out;
    }

    /**
     * Several dice played by one checker, as single destinations.
     *
     * <p>Built strictly on top of {@link #legalTurnHops}: the first hop must be
     * legal, and the second must be legal <em>from the position the first
     * leaves</em>. So every must-use and higher-die restriction already applies,
     * and a combo can never step over a blocked midpoint or skip the bar.
     *
     * <p>Runs are two dice or more. Doubles give four, so a checker may spend
     * three or all four in one tap; each extra die is another destination
     * further along the same line. Returns nothing when two dice cannot both be
     * played, or when only one die is left.
     *
     * <p>A square reachable by playing the dice in either order is reported
     * once, since the client offers one entry per destination. The two routes
     * are <em>not</em> interchangeable, though: one may pass over a blot and
     * take it while the other does not, and a route can take two — one on the
     * midpoint and one at the end. The order that takes the most is the one
     * kept. Taking a checker is the whole reason to prefer an order, and
     * silently choosing the quiet route would cost the player the hit without
     * ever telling them there was one to take.
     */
    public static List<ComboHop> legalComboHops(BoardState board, Side side, int[] remainingDice,
                                                int usedSoFar, int maxDiceUsable) {
        if (remainingDice.length < 2 || usedSoFar + 2 > maxDiceUsable) {
            return List.of();
        }

        List<ComboHop> out = new ArrayList<>(8);
        // Where each (from, to) pair landed in the list, and how many checkers
        // the run stored for it takes.
        Map<Long, Integer> at = new HashMap<>();
        Map<Long, Integer> taken = new HashMap<>();

        for (Hop first : legalTurnHops(board, side, remainingDice, usedSoFar, maxDiceUsable)) {
            // A checker that has just borne off has nowhere left to go.
            if (first.to() == MoverView.OFF) {
                continue;
            }
            extend(apply(board, side, first), side, Dice.without(remainingDice, first.die()),
                    usedSoFar + 1, maxDiceUsable, List.of(first), out, at, taken);
        }

        return out;
    }

    /**
     * Carries one checker on for as many dice as it can still spend.
     *
     * <p>{@code board} is the position after {@code path} has been played, so
     * every further hop is judged against what the run has already done — a
     * blot it took on the way is gone, and a point it left is empty.
     *
     * <p>Depth beyond two only arises on doubles, where all four dice are equal
     * and a checker therefore has exactly one continuation at each step, so the
     * search stays small.
     */
    private static void extend(BoardState board, Side side, int[] remaining, int used,
                               int maxDiceUsable, List<Hop> path, List<ComboHop> out,
                               Map<Long, Integer> at, Map<Long, Integer> taken) {
        if (remaining.length == 0 || used >= maxDiceUsable) {
            return;
        }

        int lastTo = path.getLast().to();

        for (Hop next : legalTurnHops(board, side, remaining, used, maxDiceUsable)) {
            if (next.from() != lastTo) {
                continue;               // a different checker, not this one
            }

            List<Hop> run = new ArrayList<>(path);
            run.add(next);
            record(run, out, at, taken);

            if (next.to() != MoverView.OFF) {
                extend(apply(board, side, next), side, Dice.without(remaining, next.die()),
                        used + 1, maxDiceUsable, run, out, at, taken);
            }
        }
    }

    /** Keeps one run per destination — the one that takes the most checkers. */
    private static void record(List<Hop> run, List<ComboHop> out,
                               Map<Long, Integer> at, Map<Long, Integer> taken) {
        int from = run.getFirst().from();
        int to = run.getLast().to();
        long key = (long) from * 64 + to;

        int takes = (int) run.stream().filter(Hop::hit).count();

        List<Integer> vias = run.subList(0, run.size() - 1).stream().map(Hop::to).toList();
        List<Integer> dice = run.stream().map(Hop::die).toList();
        ComboHop combo = new ComboHop(from, to, vias, dice);

        Integer seen = at.get(key);
        if (seen == null) {
            at.put(key, out.size());
            taken.put(key, takes);
            out.add(combo);
        } else if (takes > taken.get(key)) {
            // Same square, but this order takes more of the opponent's checkers
            // than the order already stored.
            out.set(seen, combo);
            taken.put(key, takes);
        }
    }

    /* ------------------------------------------------------------------
       Terminal detection
       ------------------------------------------------------------------ */

    public static boolean isFinished(BoardState board, Side side) {
        return board.off(side) == BoardState.CHECKERS_PER_SIDE;
    }

    /**
     * How decisively {@code winner} won. Марс when the loser bore off nothing;
     * кокс when they additionally still sit on the bar or in the winner's home.
     */
    public static GameResultKind resultKind(BoardState board, Side winner) {
        Side loser = winner.opponent();
        if (board.off(loser) > 0) {
            return GameResultKind.SINGLE;
        }
        if (board.bar(loser) > 0) {
            return GameResultKind.BACKGAMMON;
        }
        // The winner's home board, in the loser's normalised frame, is 19..24.
        MoverView loserView = MoverView.of(board, loser);
        for (int p = 19; p <= BoardState.POINTS; p++) {
            if (loserView.at(p) > 0) {
                return GameResultKind.BACKGAMMON;
            }
        }
        return GameResultKind.GAMMON;
    }
}
