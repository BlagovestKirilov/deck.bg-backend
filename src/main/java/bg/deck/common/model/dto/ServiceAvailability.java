package bg.deck.common.model.dto;

import bg.deck.common.enums.Scope;
import bg.deck.common.enums.ServiceState;

/**
 * One game's terms, as the cache holds them.
 *
 * <p>A copy rather than the entity: what is cached outlives the transaction it
 * was read in, and a detached entity handed round is an invitation to write
 * through it.
 *
 * @param code          the name the client knows the game by
 * @param state         whether it is being offered
 * @param requiredScope the lowest scope that may see it
 */
public record ServiceAvailability(String code, ServiceState state, Scope requiredScope) {
}
