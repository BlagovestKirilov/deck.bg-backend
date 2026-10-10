package bg.deck.tabla.model;

import bg.deck.common.model.base.BasePlayerStats;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

/** A player's табла record, in tabla's own schema, keyed by username. */
@NoArgsConstructor
@Entity
@Table(schema = "tabla", name = "player_stats")
public class TablaPlayerStats extends BasePlayerStats {

    public TablaPlayerStats(String username) {
        super(username);
    }
}
