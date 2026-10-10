package bg.deck.common.model;

import bg.deck.common.enums.Scope;
import bg.deck.common.enums.ServiceState;
import bg.deck.common.model.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One game the site can offer, and the terms it is offered on.
 *
 * <p>There is a row per game rather than a flag in configuration so that
 * turning one off is an UPDATE rather than a deploy.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "available_service")
public class AvailableService extends BaseEntity {

    /** SANTASE, TABLA, BELOT — the name the client knows the game by. */
    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ServiceState state = ServiceState.ON;

    /** The lowest scope that may see it. */
    @Enumerated(EnumType.STRING)
    @Column(name = "required_scope", nullable = false, length = 20)
    private Scope requiredScope = Scope.PUBLIC;
}
