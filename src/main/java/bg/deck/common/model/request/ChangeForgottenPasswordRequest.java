package bg.deck.common.model.request;

import bg.deck.common.constant.ValidationConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ChangeForgottenPasswordRequest(
        @NotBlank(message = ValidationConstants.PASSWORD_EMPTY)
        @Size(min = ValidationConstants.PASSWORD_MIN, max = ValidationConstants.PASSWORD_MAX, message = ValidationConstants.PASSWORD_SIZE)
        @Pattern(regexp = ValidationConstants.PASSWORD_PATTERN, message = ValidationConstants.PASSWORD_PATTERN_MSG)
        String newPassword,

        @NotNull(message = ValidationConstants.TOKEN_NULL)
        UUID token
) {

    /**
     * A record prints every component, and this one carries a secret —
     * a password or a one-time token — that must never reach a log.
     */
    @Override
    public String toString() {
        return "ChangeForgottenPasswordRequest[newPassword=***, token=***]";
    }
}
