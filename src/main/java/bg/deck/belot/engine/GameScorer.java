package bg.deck.belot.engine;

import bg.deck.belot.enums.Team;

/**
 * Whether the game is over. RULES §9.
 *
 * <p>A game runs to 151. Cross it and the higher total wins — both teams can
 * cross in the same deal, and then it is simply the larger score.
 *
 * <p>"С капо не се излиза": a team cannot finish on a capot. If the deal just
 * played was one and it would have ended the game, another deal follows.
 */
public final class GameScorer {

    /** The line a team has to cross. */
    public static final int TARGET = 151;

    private GameScorer() {
    }

    /**
     * @param northSouth        what that team has on the sheet
     * @param eastWest          what the other has
     * @param lastDealWasCapot  true when the deal just finished took every trick
     */
    public static GameVerdict verdict(int northSouth, int eastWest, boolean lastDealWasCapot) {
        int leader = Math.max(northSouth, eastWest);
        if (leader < TARGET) {
            return GameVerdict.playOn();
        }
        if (lastDealWasCapot) {
            // The line was crossed, but not like this: one more deal.
            return GameVerdict.playOn();
        }
        // OPEN 10 — level on or above the line. Another deal is played here,
        // since a game of belot is not left drawn.
        if (northSouth == eastWest) {
            return GameVerdict.playOn();
        }
        return GameVerdict.wonBy(northSouth > eastWest ? Team.NORTH_SOUTH : Team.EAST_WEST);
    }
}
