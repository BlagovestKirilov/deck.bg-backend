package bg.deck.common.model.request;

import bg.deck.common.constant.ValidationConstants;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * The token from the account-deletion email, sent in the body rather than the
 * query string so it does not end up in access logs or a {@code Referer}.
 */
public record ConfirmDeletionRequest(
        @NotNull(message = ValidationConstants.TOKEN_NULL)
        UUID token
) {

    /**
     * A record prints every component, and this one carries a secret —
     * a password or a one-time token — that must never reach a log.
     */
    @Override
    public String toString() {
        return "ConfirmDeletionRequest[token=***]";
    }
}
