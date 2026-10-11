package bg.deck.common.model.request;

import bg.deck.common.enums.Scope;
import jakarta.validation.constraints.NotNull;

/** How far into the site an account can see. */
public record AdminScopeRequest(@NotNull Scope scope) {
}
