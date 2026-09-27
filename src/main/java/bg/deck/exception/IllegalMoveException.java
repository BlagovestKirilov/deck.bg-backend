package bg.deck.exception;

/**
 * A move that was legal a moment ago, or never was.
 *
 * <p>Tapping a card as the turn moves on, bidding into an auction that has
 * just closed, playing at a table the last card has just finished — all of
 * them are ordinary things a client does when the socket is a few hundred
 * milliseconds behind the server. They used to come out of the engine as
 * {@code IllegalArgumentException} or {@code IllegalStateException}, reach the
 * catch-all, and be answered with a 500 and a stack trace in the log.
 *
 * <p>That is wrong twice over. The caller is told the server broke when the
 * server did exactly the right thing, and a log full of expected races is a
 * log nobody reads when something does break.
 */
public class IllegalMoveException extends RuntimeException {

    public IllegalMoveException(String message) {
        super(message);
    }

    public IllegalMoveException(String message, Throwable cause) {
        super(message, cause);
    }
}
