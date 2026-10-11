package bg.deck.common.model;

import bg.deck.common.enums.Scope;
import bg.deck.common.model.base.BaseUser;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Setter
@Getter
@RequiredArgsConstructor
@Entity
@Table(name = "users")
public class User extends BaseUser {

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    /**
     * How far this account can see: {@link Scope#PUBLIC} for almost everyone,
     * higher for the few who are meant to reach a game before the rest.
     * Initialised here as well as defaulted in the column, or Hibernate writes
     * an explicit null on insert and the constraint refuses it.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Scope scope = Scope.PUBLIC;

    /**
     * When this account was last here: a password login or a token refresh,
     * whichever came last. Written only by {@code UserAccountService.markSeen},
     * a one-column UPDATE, so it never carries a stale copy of the rest of the
     * row back over a change made in the meantime. Empty for an account that
     * has not been back since the column was added.
     */
    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
