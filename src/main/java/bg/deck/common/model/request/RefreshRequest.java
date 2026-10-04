package bg.deck.common.model.request;

import bg.deck.common.constant.ValidationConstants;
import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = ValidationConstants.REFRESH_TOKEN_EMPTY)
        String refreshToken
) {

    /**
     * A record prints every component, and this one carries a secret —
     * a password or a one-time token — that must never reach a log.
     */
    @Override
    public String toString() {
        return "RefreshRequest[refreshToken=***]";
    }
}
