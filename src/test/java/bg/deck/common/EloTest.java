package bg.deck.common;

import bg.deck.common.constant.RankingConstants;
import bg.deck.common.model.base.BasePlayerStats;
import bg.deck.common.util.Elo;
import bg.deck.santase.model.SantasePlayerStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rating a two-player game moves — the formula the ranking service used,
 * moved so each game settles its own result.
 */
@DisplayName("Elo for a two-player game")
class EloTest {

    @Test
    @DisplayName("two equal ratings: the winner gains half the K factor and the loser loses it")
    void equalRatingsMoveByHalfK() {
        int k = RankingConstants.K_PLACEMENT;
        assertThat(Elo.delta(1500, 1500, true, 1)).isEqualTo(Math.round(k / 2.0f));
        assertThat(Elo.delta(1500, 1500, false, 1)).isEqualTo(-Math.round(k / 2.0f));
    }

    @Test
    @DisplayName("past the placement games the smaller ranked K factor applies")
    void rankedGamesUseTheRankedK() {
        int ranked = RankingConstants.PLACEMENT_GAMES;
        assertThat(Elo.delta(1500, 1500, true, ranked))
                .isEqualTo(Math.round(RankingConstants.K_RANKED / 2.0f));
    }

    @Test
    @DisplayName("an upset moves the rating further than an expected win")
    void anUpsetMovesFurther() {
        assertThat(Elo.delta(1300, 1700, true, 1)).isGreaterThan(Elo.delta(1700, 1300, true, 1));
    }

    @Test
    @DisplayName("settling a game counts it in both records, each rated against the other's rating before it")
    void settleWritesBothRecords() {
        SantasePlayerStats winner = new SantasePlayerStats("petko91");
        SantasePlayerStats loser = new SantasePlayerStats("gosho");

        Elo.settle(winner, loser);

        assertThat(winner.getWins()).isEqualTo(1);
        assertThat(loser.getLosses()).isEqualTo(1);
        assertThat(winner.getRating() - BasePlayerStats.STARTING_RATING).isEqualTo(Elo.delta(1500, 1500, true, 1));
        assertThat(loser.getRating() - BasePlayerStats.STARTING_RATING).isEqualTo(Elo.delta(1500, 1500, false, 1));
    }

    @Test
    @DisplayName("a missing record — a deleted account's seat — leaves the other settled against a starting rating")
    void aMissingRecordIsSkipped() {
        SantasePlayerStats winner = new SantasePlayerStats("petko91");

        Elo.settle(winner, null);

        assertThat(winner.getWins()).isEqualTo(1);
        assertThat(winner.getRating() - BasePlayerStats.STARTING_RATING).isEqualTo(Elo.delta(1500, 1500, true, 1));
    }
}
