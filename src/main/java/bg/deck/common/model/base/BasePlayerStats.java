package bg.deck.common.model.base;

import bg.deck.common.enums.Rank;
import bg.deck.common.util.RankLadder;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A player's record at one game: wins, losses, the rating behind the rank, and
 * the rank.
 *
 * <p>The columns only. Each game keeps its own table of them in its own schema
 * — {@code santase.player_stats}, {@code tabla.player_stats} — keyed by
 * username, so no game's record points at the accounts table or at another
 * game's.
 */
@Getter
@Setter
@NoArgsConstructor
@MappedSuperclass
public abstract class BasePlayerStats extends BaseEntity {

    /** Everyone starts level, and the first games say where they stand. */
    public static final int STARTING_RATING = 1500;

    @Column(nullable = false, length = 20, unique = true)
    private String username;

    @Column(nullable = false)
    private int wins;

    @Column(nullable = false)
    private int losses;

    @Column(nullable = false)
    private int rating = STARTING_RATING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rank rank = Rank.UNRANKED;

    protected BasePlayerStats(String username) {
        this.username = username;
    }

    public int totalGames() {
        return wins + losses;
    }

    /**
     * One finished game, with what it did to the rating — together, because
     * they are one event, and a record with one and not the other cannot be
     * read. The rank is worked out from the record after it.
     */
    public void record(boolean won, int ratingDelta) {
        if (won) {
            wins++;
        } else {
            losses++;
        }
        rating += ratingDelta;
        rank = RankLadder.rankFor(rating, totalGames());
    }
}
