package bg.deck.common.exception;

import static bg.deck.common.constant.ExceptionConstants.USER_NOT_FOUND;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String username) {
        super(String.format(USER_NOT_FOUND, username));
    }
}
