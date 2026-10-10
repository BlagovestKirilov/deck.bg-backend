package bg.deck.tabla.model;

import bg.deck.common.model.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * One player's seat in one табла game.
 *
 * <p>Named by username, in tabla's own schema, so a seat points at nothing
 * outside it. A deleted account's seats are renamed to the tombstone name,
 * which no account can take.
 */
@Setter
@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(schema = "tabla", name = "seat")
public class TablaSeat extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String username;

    private Integer inactivityCount;
}
