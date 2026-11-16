package bg.deck.common.model.request;

import bg.deck.common.enums.Reaction;
import jakarta.validation.constraints.NotNull;

/**
 * A reaction sent to the table the sender is sitting at. No table and no
 * seat: those are whoever the token says.
 *
 * @param reaction one of the fixed set
 */
public record ReactionRequest(@NotNull Reaction reaction) {
}
