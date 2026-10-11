package bg.deck.common.exception;

/** A request the server will not read the way it was asked: answered 400 with the message. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
