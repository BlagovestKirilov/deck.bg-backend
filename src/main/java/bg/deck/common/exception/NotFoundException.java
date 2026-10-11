package bg.deck.common.exception;

/**
 * Something an admin named that is not there: a game code or a username.
 * Answered with 404 and the message.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
