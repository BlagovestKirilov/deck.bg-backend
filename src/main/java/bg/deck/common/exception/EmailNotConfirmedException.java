package bg.deck.common.exception;

import bg.deck.common.constant.ExceptionConstants;

public class EmailNotConfirmedException extends RuntimeException {
    public EmailNotConfirmedException(String email) {
        super(String.format(ExceptionConstants.EMAIL_NOT_CONFIRMED, email));
    }
}