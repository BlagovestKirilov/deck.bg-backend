package bg.deck.common.util;

import bg.deck.common.model.base.BasePlayerStats;
import lombok.experimental.UtilityClass;

/**
 * The rating a two-player game moves: santase's and табла's.
 *
 * <p>The same formula the ranking has always used, moved here from the
 * ranking service so each game can settle its own result in its own table.
 * Belot's pairs are rated by {@code TeamElo}.
 */
@UtilityClass
public class Elo {

    /** How far one result moves a rating: the K factor for how settled it is, against the expected score. */
    public static int delta(int rating, int opponentRating, boolean won, int gamesPlayed) {
        int kFactor = RankLadder.kFactor(gamesPlayed);
        double expected = 1.0 / (1.0 + Math.pow(10, (opponentRating - rating) / 400.0));
        return (int) Math.round(kFactor * ((won ? 1 : 0) - expected));
    }

    /**
     * Writes one finished game into both records: the win and the loss, each
     * rating moved against the other's rating before the game, and each rank.
     *
     * <p>Either record may be missing — the seat of an account deleted during
     * the game — and the other is settled against a starting rating. The game
     * is counted in the K factor, as it always has been: a player's tenth game
     * is rated as their tenth.
     */
    public static void settle(BasePlayerStats winner, BasePlayerStats loser) {
        int winnerRating = winner == null ? BasePlayerStats.STARTING_RATING : winner.getRating();
        int loserRating = loser == null ? BasePlayerStats.STARTING_RATING : loser.getRating();
        if (winner != null) {
            winner.record(true, delta(winnerRating, loserRating, true, winner.totalGames() + 1));
        }
        if (loser != null) {
            loser.record(false, delta(loserRating, winnerRating, false, loser.totalGames() + 1));
        }
    }
}
