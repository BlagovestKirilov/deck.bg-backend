package bg.deck.common.model.request;

import bg.deck.common.enums.Scope;
import bg.deck.common.enums.ServiceState;
import jakarta.validation.constraints.NotNull;

/** A game switched on or off, and who may see it. */
public record AdminServiceRequest(@NotNull ServiceState state, @NotNull Scope requiredScope) {
}
