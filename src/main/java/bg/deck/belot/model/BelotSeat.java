package bg.deck.belot.model;

import bg.deck.belot.engine.Seat;
import bg.deck.belot.engine.Team;
import bg.deck.common.model.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One place at a table, and who is in it.
 *
 * <p>The player is named, not referenced: the username is what the token
 * carries, and belot points at nothing in another schema.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(schema = "belot", name = "seat")
public class BelotSeat extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "game_id", nullable = false)
    private BelotGame game;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Seat seat;

    @Column(nullable = false, length = 20)
    private String username;

    public BelotSeat(Seat seat, String username) {
        this.seat = seat;
        this.username = username;
    }

    public Team team() {
        return Team.of(seat);
    }
}
