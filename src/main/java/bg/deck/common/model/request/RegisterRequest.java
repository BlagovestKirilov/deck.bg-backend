package bg.deck.common.model.request;

import bg.deck.common.constant.ValidationConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = ValidationConstants.USERNAME_EMPTY)
        @Size(min = ValidationConstants.USERNAME_MIN, max = ValidationConstants.USERNAME_MAX, message = ValidationConstants.USERNAME_SIZE)
        @Pattern(regexp = ValidationConstants.ALPHANUMERIC_PATTERN, message = ValidationConstants.USERNAME_PATTERN)
        String username,

        @NotBlank(message = ValidationConstants.PASSWORD_EMPTY)
        @Size(min = ValidationConstants.PASSWORD_MIN, max = ValidationConstants.PASSWORD_MAX, message = ValidationConstants.PASSWORD_SIZE)
        @Pattern(regexp = ValidationConstants.PASSWORD_PATTERN, message = ValidationConstants.PASSWORD_PATTERN_MSG)
        String password,

        @NotBlank(message = ValidationConstants.EMAIL_EMPTY)
        @Email(message = ValidationConstants.EMAIL_INVALID)
        String email
) {

    /**
     * A record prints every component, and this one carries a secret —
     * a password or a one-time token — that must never reach a log.
     */
    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", password=***, email=" + email + "]";
    }
}
