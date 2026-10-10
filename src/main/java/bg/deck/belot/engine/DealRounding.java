package bg.deck.belot.engine;

/**
 * Turning a deal's points into what goes on the score sheet. RULES §8.
 *
 * <p>Two scores are rounded together, never one at a time, because the sheet
 * has to add up: a deal worth 162 is recorded as 16 whoever took what, and a
 * rounding that loses or invents a point is the kind of arithmetic players
 * notice immediately.
 *
 * <p>So: each score goes to its nearest ten, with a five going <em>down</em>.
 * If the two then add up to the deal, that is the answer — and most of the time
 * they do. When they fall a point short, one of them has to come up:
 *
 * <ul>
 *   <li>both scores ending in 4 — the calling team takes the lower rounding and
 *       the other takes the higher (154 and 104 are 15 and 11);</li>
 *   <li>otherwise the team that took more goes up.</li>
 * </ul>
 *
 * <p>When they come out a point over instead, the team that took fewer goes
 * down: 86 and 76 are 9 and 7, not 9 and 8.
 */
public final class DealRounding {

    private static final int TEN = 10;
    /** Both scores ending in this is the case the calling team settles. */
    private static final int THE_AWKWARD_REMAINDER = 4;

    private DealRounding() {
    }

    /**
     * The two scores as the sheet records them.
     *
     * <p>Bonuses are part of the points passed in — declarations, the last
     * trick, capot — so the deal's own total is simply what the two add up to.
     */
    public static RecordedScore split(int callerPoints, int opponentPoints) {
        int target = nearest(callerPoints + opponentPoints);
        int caller = nearest(callerPoints);
        int opponents = nearest(opponentPoints);
        int missing = target - (caller + opponents);

        if (missing > 0) {
            boolean bothEndInFour = callerPoints % TEN == THE_AWKWARD_REMAINDER
                    && opponentPoints % TEN == THE_AWKWARD_REMAINDER;
            if (bothEndInFour || opponentPoints > callerPoints) {
                opponents += missing;
            } else {
                caller += missing;
            }
        } else if (missing < 0) {
            if (callerPoints <= opponentPoints) {
                caller += missing;
            } else {
                opponents += missing;
            }
        }
        return new RecordedScore(caller, opponents);
    }

    /** The nearest ten, with a five going down: 85 is 8, 86 is 9. */
    public static int nearest(int points) {
        return (points + TEN / 2 - 1) / TEN;
    }
}
