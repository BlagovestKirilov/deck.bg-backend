package bg.deck.belot.model;

import bg.deck.enums.Rank;
import bg.deck.model.base.BaseEntity;
import bg.deck.util.RankLadder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What belot remembers about a player between games.
 *
 * <p>Keyed by username, like everything else here, so belot still points at
 * nothing in another schema.
 *
 * <p>The rating is a team rating applied to one player: a belot result moves
 * both partners, and {@code TeamElo} says by how much. The rank is stored
 * rather than worked out on the way out, so a leaderboard can be a query.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(schema = "belot", name = "player_stats")
public class BelotPlayerStats extends BaseEntity {

    /** Everyone starts level, and the first ten games say where they stand. */
    public static final int STARTING_RATING = 1500;

    @Column(nullable = false, length = 20, unique = true)
    private String username;

    @Column(nullable = false)
    private int games;

    @Column(nullable = false)
    private int wins;

    @Column(nullable = false)
    private int losses;

    @Column(nullable = false)
    private int rating = STARTING_RATING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rank rank = Rank.UNRANKED;

    public BelotPlayerStats(String username) {
        this.username = username;
    }

    /**
     * One finished game, with what it did to the rating.
     *
     * <p>The two together, because they are one event: a win written without
     * its rating move, or a move written without the game behind it, leaves a
     * record that cannot be read.
     */
    public void record(boolean won, int ratingDelta) {
        games++;
        if (won) {
            wins++;
        } else {
            losses++;
        }
        rating += ratingDelta;
        rank = RankLadder.rankFor(rating, games);
    }
}
