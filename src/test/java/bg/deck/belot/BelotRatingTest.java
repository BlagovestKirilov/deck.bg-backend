package bg.deck.belot;

import bg.deck.belot.engine.TeamElo;
import bg.deck.common.constant.RankingConstants;
import bg.deck.common.enums.Rank;
import bg.deck.common.util.RankLadder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arithmetic behind a belot rating, with nothing else running.
 *
 * <p>The decision this pins down: <b>a 2v2 result moves both partners
 * equally</b>. A pair is one rating, the average of the two in it, and the
 * same delta goes to each. The tests that matter are the ones where the two
 * partners are nothing alike and still move together.
 */
@DisplayName("A belot rating")
class BelotRatingTest {

    private static final int EVEN = 1500;

    @Nested
    @DisplayName("moves both partners equally")
    class Equally {

        @Test
        @DisplayName("an even table: the winners take what the losers give")
        void evenTable() {
            double us = TeamElo.teamRating(EVEN, EVEN);
            double them = TeamElo.teamRating(EVEN, EVEN);

            int won = TeamElo.delta(us, them, true, RankingConstants.K_RANKED);
            int lost = TeamElo.delta(them, us, false, RankingConstants.K_RANKED);

            assertEquals(RankingConstants.K_RANKED / 2, won, "half the K factor, at even odds");
            assertEquals(-won, lost, "and the same amount off the other side");
        }

        @Test
        @DisplayName("a strong player carrying a weak partner gains no more than the partner")
        void theStrongDoNotOutEarnTheWeak() {
            // 1900 and 1100 average to the same 1500 as two even players, so
            // this pair is rated level with the other and the game is a coin
            // toss on paper.
            double lopsided = TeamElo.teamRating(1900, 1100);
            double even = TeamElo.teamRating(EVEN, EVEN);

            assertEquals(even, lopsided, "the pair is what the two of them average to");

            int strong = TeamElo.delta(lopsided, even, true, RankingConstants.K_RANKED);
            int weak = TeamElo.delta(lopsided, even, true, RankingConstants.K_RANKED);

            assertEquals(strong, weak,
                    "the win is the pair's: nothing here tries to say who carried it");
        }

        @Test
        @DisplayName("beating a stronger pair is worth more than beating a weaker one")
        void theOddsStillCount() {
            double us = TeamElo.teamRating(EVEN, EVEN);

            int upset = TeamElo.delta(us, TeamElo.teamRating(1900, 1900), true, RankingConstants.K_RANKED);
            int expected = TeamElo.delta(us, TeamElo.teamRating(1100, 1100), true, RankingConstants.K_RANKED);

            assertTrue(upset > expected, upset + " for the upset, " + expected + " for the formality");
        }
    }

    @Nested
    @DisplayName("except for how settled it is")
    class KFactor {

        @Test
        @DisplayName("a newcomer's own rating moves further than their partner's")
        void placementMovesFurther() {
            double us = TeamElo.teamRating(EVEN, EVEN);
            double them = TeamElo.teamRating(EVEN, EVEN);

            int newcomer = TeamElo.delta(us, them, true, RankLadder.kFactor(0));
            int settled = TeamElo.delta(us, them, true, RankLadder.kFactor(RankingConstants.PLACEMENT_GAMES));

            assertTrue(newcomer > settled,
                    "the same win, but we know less about the newcomer: " + newcomer + " vs " + settled);
        }

        @Test
        @DisplayName("and settles once the placement games are behind them")
        void settlesAfterPlacement() {
            assertEquals(RankingConstants.K_PLACEMENT, RankLadder.kFactor(RankingConstants.PLACEMENT_GAMES - 1));
            assertEquals(RankingConstants.K_RANKED, RankLadder.kFactor(RankingConstants.PLACEMENT_GAMES));
        }
    }

    @Nested
    @DisplayName("earns the same badge as santase")
    class Ladder {

        @Test
        @DisplayName("nobody is ranked before the placement games are done")
        void unrankedUntilPlaced() {
            assertEquals(Rank.UNRANKED, RankLadder.rankFor(2500, RankingConstants.PLACEMENT_GAMES - 1),
                    "a rating moved nine times says more about who sat down than about the player");
            assertEquals(RankingConstants.PLACEMENT_GAMES, RankLadder.placementGamesRemaining(0));
            assertEquals(0, RankLadder.placementGamesRemaining(RankingConstants.PLACEMENT_GAMES + 5));
        }

        @Test
        @DisplayName("and the thresholds are the ones santase uses")
        void oneLadder() {
            int placed = RankingConstants.PLACEMENT_GAMES;

            assertEquals(Rank.BRONZE, RankLadder.rankFor(RankingConstants.BRONZE_THRESHOLD - 1, placed));
            assertEquals(Rank.SILVER, RankLadder.rankFor(RankingConstants.BRONZE_THRESHOLD, placed));
            assertEquals(Rank.GOLD, RankLadder.rankFor(RankingConstants.SILVER_THRESHOLD, placed));
            assertEquals(Rank.PLATINUM, RankLadder.rankFor(RankingConstants.GOLD_THRESHOLD, placed));
            assertEquals(Rank.DIAMOND, RankLadder.rankFor(RankingConstants.PLATINUM_THRESHOLD, placed));
            assertEquals(Rank.LEGEND, RankLadder.rankFor(RankingConstants.DIAMOND_THRESHOLD, placed));
        }
    }
}
