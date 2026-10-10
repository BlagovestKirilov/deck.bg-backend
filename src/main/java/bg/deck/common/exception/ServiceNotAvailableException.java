package bg.deck.common.exception;

import lombok.Getter;

/**
 * Thrown when a player asks to start a game that is not being offered to them
 * — switched off, or kept for a few testers while it is still being built.
 *
 * <p>Answered with 404. A game somebody may not have should not announce that
 * it exists: a beta before its time is not a locked door with a label on it, it
 * is simply not there.
 */
@Getter
public class ServiceNotAvailableException extends RuntimeException {

    /** The game that was asked for, for the log line. It is not in the answer. */
    private final String code;

    public ServiceNotAvailableException(String code) {
        super(code + " is not on offer.");
        this.code = code;
    }
}
