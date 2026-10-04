package bg.deck.common.model;

import bg.deck.common.enums.GameType;
import bg.deck.common.enums.Rank;
import bg.deck.common.model.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-game wins, losses and rating.
 *
 * <p>Replaces the single {@code santase_wins}/{@code santase_losses}/{@code rank}
 * /{@code rank_rating} set that used to live on {@code users}: Elo from two
 * unrelated games must not share one number, so a Legend at Santase starts
 * unranked at табла.
 *
 * <p>A row per (user, game) is created eagerly at registration and backfilled for
 * existing users, so nothing ever has to create one under concurrency.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_game_stats",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_game_stats", columnNames = {"user_id", "game_type"}))
public class UserGameStats extends BaseEntity {

    public static final int STARTING_RATING = 1500;

    @ManyToOne(optional = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_type", nullable = false, length = 20)
    private GameType gameType;

    @Column(nullable = false)
    private Integer wins;

    @Column(nullable = false)
    private Integer losses;

    @Column(nullable = false)
    private Integer rating;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rank rank;

    public static UserGameStats fresh(User user, GameType gameType) {
        return UserGameStats.builder()
                .user(user)
                .gameType(gameType)
                .wins(0)
                .losses(0)
                .rating(STARTING_RATING)
                .rank(Rank.UNRANKED)
                .build();
    }

    public void incrementWins() {
        this.wins = this.wins + 1;
    }

    public void incrementLosses() {
        this.losses = this.losses + 1;
    }

    public int totalGames() {
        return wins + losses;
    }
}
