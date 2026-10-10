package bg.deck.common.exception;

import static bg.deck.common.constant.ExceptionConstants.EMAIL_CONFIRMATION_NOT_FOUND;

public class EmailConfirmationNotFoundException extends RuntimeException {
    public EmailConfirmationNotFoundException(String confirmationToken) {
        super(String.format(EMAIL_CONFIRMATION_NOT_FOUND, confirmationToken));
    }
}
