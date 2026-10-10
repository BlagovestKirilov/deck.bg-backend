package bg.deck.santase.model;

import bg.deck.common.model.base.BasePlayerStats;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

/** A player's сантасе record, in santase's own schema, keyed by username. */
@NoArgsConstructor
@Entity
@Table(schema = "santase", name = "player_stats")
public class SantasePlayerStats extends BasePlayerStats {

    public SantasePlayerStats(String username) {
        super(username);
    }
}
