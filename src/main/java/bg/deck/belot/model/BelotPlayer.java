package bg.deck.belot.model;

import bg.deck.common.model.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Someone who has played belot, as belot knows them.
 *
 * <p>Known by username and nothing else. That name is the token's subject, it
 * is fixed at registration and there is no rename anywhere in the service, so
 * it identifies a person without belot ever reading {@code public.users}. The
 * row is created the first time its owner asks belot for anything.
 *
 * <p>Deliberately not a foreign key to a user: the whole point of the schema is
 * that these tables can be lifted into a service of their own without unpicking
 * a join. See {@code docs/belot/BUILD.md}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(schema = "belot", name = "player")
public class BelotPlayer extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String username;

    public BelotPlayer(String username) {
        this.username = username;
    }
}
