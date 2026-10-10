package bg.deck.belot.engine;

import bg.deck.belot.enums.DealResult;

/**
 * Settling one deal. RULES §8.
 *
 * <p>Three ways it ends, decided by nothing more than which side took more:
 *
 * <ul>
 *   <li><b>made</b> — the callers took more, and each side records its own —
 *       unless the deal was doubled, when the callers take the lot;</li>
 *   <li><b>вътре</b> — the others took more, and record <em>everything</em>,
 *       both sides' points together, while the callers record nothing;</li>
 *   <li><b>висящи</b> — level. The callers record nothing and their points wait
 *       for whoever wins the next deal; the others record theirs as usual.</li>
 * </ul>
 *
 * <p>The points passed in are the finished ones: cards, declarations, the last
 * trick and capot. A contra doubles what goes on the sheet, and points left
 * hanging carry forward already doubled.
 *
 * <p>A doubled deal is all or nothing: whoever wins it records both sides'
 * points together, doubled or quadrupled, and the losers record nothing.
 * That is what a contra is a bet on.
 */
public final class DealScorer {

    private DealScorer() {
    }

    /**
     * @param callerPoints   everything the calling team took
     * @param opponentPoints everything the other team took
     * @param multiplier     1, or 2 after a contra, or 4 after a recontra
     * @param carriedIn      points hanging from earlier deals, waiting to be won
     */
    public static DealOutcome score(int callerPoints, int opponentPoints, int multiplier, int carriedIn) {
        if (callerPoints > opponentPoints) {
            return made(callerPoints, opponentPoints, multiplier, carriedIn);
        }
        if (opponentPoints > callerPoints) {
            return inside(callerPoints, opponentPoints, multiplier, carriedIn);
        }
        return hanging(callerPoints, opponentPoints, multiplier, carriedIn);
    }

    /**
     * Each side records what it took, and the hanging points go to the winner.
     *
     * <p>Doubled, the callers take everything instead — the mirror of a
     * doubled deal going вътре — rounded as one number, then multiplied.
     */
    private static DealOutcome made(int callerPoints, int opponentPoints, int multiplier, int carriedIn) {
        if (multiplier > 1) {
            int everything = DealRounding.nearest(callerPoints + opponentPoints);
            return new DealOutcome(
                    DealResult.MADE,
                    new RecordedScore(everything * multiplier + carriedIn, 0),
                    0);
        }
        RecordedScore recorded = DealRounding.split(callerPoints, opponentPoints);
        return new DealOutcome(
                DealResult.MADE,
                new RecordedScore(recorded.caller() * multiplier + carriedIn, recorded.opponents() * multiplier),
                0);
    }

    /**
     * Вътре. The others take the lot — "отборът, обявил вида ѝ, не записва
     * нищо" — so the whole deal is rounded as one number, not as two.
     */
    private static DealOutcome inside(int callerPoints, int opponentPoints, int multiplier, int carriedIn) {
        int everything = DealRounding.nearest(callerPoints + opponentPoints);
        return new DealOutcome(
                DealResult.INSIDE,
                new RecordedScore(0, everything * multiplier + carriedIn),
                0);
    }

    /**
     * Висящи. The callers write nothing down and their points stay on the table
     * for the next deal; the others record theirs now.
     */
    private static DealOutcome hanging(int callerPoints, int opponentPoints, int multiplier, int carriedIn) {
        RecordedScore recorded = DealRounding.split(callerPoints, opponentPoints);
        return new DealOutcome(
                DealResult.HANGING,
                new RecordedScore(0, recorded.opponents() * multiplier),
                // Already doubled, and anything hanging from before stays with it.
                recorded.caller() * multiplier + carriedIn);
    }
}
