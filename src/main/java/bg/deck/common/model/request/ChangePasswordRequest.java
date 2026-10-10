package bg.deck.common.model.request;

import bg.deck.common.constant.ValidationConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = ValidationConstants.PASSWORD_EMPTY)
        @Size(min = ValidationConstants.PASSWORD_MIN, max = ValidationConstants.PASSWORD_MAX, message = ValidationConstants.PASSWORD_SIZE)
        @Pattern(regexp = ValidationConstants.PASSWORD_PATTERN, message = ValidationConstants.PASSWORD_PATTERN_MSG)
        String currentPassword,

        @NotBlank(message = ValidationConstants.PASSWORD_EMPTY)
        @Size(min = ValidationConstants.PASSWORD_MIN, max = ValidationConstants.PASSWORD_MAX, message = ValidationConstants.PASSWORD_SIZE)
        @Pattern(regexp = ValidationConstants.PASSWORD_PATTERN, message = ValidationConstants.PASSWORD_PATTERN_MSG)
        String newPassword
) {

    /**
     * A record prints every component, and this one carries a secret —
     * a password or a one-time token — that must never reach a log.
     */
    @Override
    public String toString() {
        return "ChangePasswordRequest[currentPassword=***, newPassword=***]";
    }
}
