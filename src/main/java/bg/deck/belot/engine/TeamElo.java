package bg.deck.belot.engine;

/**
 * What a finished belot game does to four ratings.
 *
 * <p>Belot is played in pairs, and the result belongs to the pair: a team is
 * rated as the average of the two sitting in it, and <b>both partners move by
 * the same amount</b>. Nothing here tries to work out who carried the game.
 * The server can see who took tricks, but tricks are won with the cards you
 * were dealt and with what your partner led — a contribution score would put
 * a number on luck and then charge the unlucky partner for it.
 *
 * <p>One thing is still per player: the K factor. That is not about how much
 * of the game they played, it is about how sure we are of the rating they
 * came in with. A newcomer's first ten games move their own number further
 * than a settled player's, exactly as at santase, so two partners can take
 * the same result and end up moving by different amounts.
 *
 * <p>Pure arithmetic, no database: {@code BelotStatsService} reads the four
 * ratings, asks here, and writes the four back.
 */
public final class TeamElo {

    private TeamElo() {
    }

    /** A pair's rating: the two of them, evenly. */
    public static double teamRating(int one, int other) {
        return (one + other) / 2.0;
    }

    /** The share of the result a pair was expected to take, between 0 and 1. */
    public static double expected(double teamRating, double opponentRating) {
        return 1.0 / (1.0 + Math.pow(10, (opponentRating - teamRating) / 400.0));
    }

    /**
     * How far one player's rating moves.
     *
     * @param teamRating     their pair's rating
     * @param opponentRating the other pair's rating
     * @param won            whether their pair took the game
     * @param kFactor        how hard a result moves this player's own rating
     */
    public static int delta(double teamRating, double opponentRating, boolean won, int kFactor) {
        double result = won ? 1.0 : 0.0;
        return (int) Math.round(kFactor * (result - expected(teamRating, opponentRating)));
    }
}
