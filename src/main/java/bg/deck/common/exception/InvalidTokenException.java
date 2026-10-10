package bg.deck.common.exception;

import static bg.deck.common.constant.ExceptionConstants.INVALID_TOKEN;

public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException() {
        super(INVALID_TOKEN);
    }
}
