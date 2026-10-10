package bg.deck.common.util;

import bg.deck.common.constant.RankingConstants;
import bg.deck.common.enums.Rank;
import lombok.experimental.UtilityClass;

/**
 * The one ladder from a rating to a rank.
 *
 * <p>Shared by santase and belot on purpose. A Gold badge has to mean the same
 * thing wherever it is worn, and the profile page puts the two side by side: a
 * second copy of these thresholds would drift the first time one of them moved
 * and nobody would notice until the badges disagreed on screen.
 *
 * <p>Nothing below a full set of placement games is ranked at all. A rating
 * that has only been moved twice says more about who happened to sit down than
 * about the player.
 */
@UtilityClass
public class RankLadder {

    /** Where a player stands, given the rating they hold and the games behind it. */
    public static Rank rankFor(int rating, int gamesPlayed) {
        if (gamesPlayed < RankingConstants.PLACEMENT_GAMES) {
            return Rank.UNRANKED;
        }

        if (rating < RankingConstants.BRONZE_THRESHOLD) return Rank.BRONZE;
        if (rating < RankingConstants.SILVER_THRESHOLD) return Rank.SILVER;
        if (rating < RankingConstants.GOLD_THRESHOLD) return Rank.GOLD;
        if (rating < RankingConstants.PLATINUM_THRESHOLD) return Rank.PLATINUM;
        if (rating < RankingConstants.DIAMOND_THRESHOLD) return Rank.DIAMOND;

        return Rank.LEGEND;
    }

    /** How many more games before a rank is given at all. */
    public static int placementGamesRemaining(int gamesPlayed) {
        return Math.max(0, RankingConstants.PLACEMENT_GAMES - gamesPlayed);
    }

    /** How hard a result moves a rating, which is a question about certainty. */
    public static int kFactor(int gamesPlayed) {
        return gamesPlayed < RankingConstants.PLACEMENT_GAMES
                ? RankingConstants.K_PLACEMENT
                : RankingConstants.K_RANKED;
    }
}
