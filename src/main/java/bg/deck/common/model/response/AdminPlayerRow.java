package bg.deck.common.model.response;

import bg.deck.common.enums.AccountRole;
import bg.deck.common.enums.Scope;

import java.time.Instant;

/** An account, as the admin page lists it. */
public record AdminPlayerRow(String username, String email, AccountRole role, Scope scope,
                             boolean emailConfirmed, Instant createdAt, Instant lastLoginAt) {
}
