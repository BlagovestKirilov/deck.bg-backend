package bg.deck.belot.model;

import bg.deck.model.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What belot remembers about a player between games.
 *
 * <p>Keyed by username, like everything else here, so belot still points at
 * nothing in another schema. Counts only: a rating would need an answer to
 * how a 2v2 result moves two partners, and a column holding a number nobody
 * has agreed on is worse than no column.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(schema = "belot", name = "player_stats")
public class BelotPlayerStats extends BaseEntity {

    @Column(nullable = false, length = 20, unique = true)
    private String username;

    @Column(nullable = false)
    private int games;

    @Column(nullable = false)
    private int wins;

    @Column(nullable = false)
    private int losses;

    public BelotPlayerStats(String username) {
        this.username = username;
    }

    public void record(boolean won) {
        games++;
        if (won) {
            wins++;
        } else {
            losses++;
        }
    }
}
