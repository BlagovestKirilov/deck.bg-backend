package bg.deck.common.exception;

import static bg.deck.common.constant.ExceptionConstants.EMAIL_ALREADY_EXISTS;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String email) {
        super(String.format(EMAIL_ALREADY_EXISTS, email));
    }
}
