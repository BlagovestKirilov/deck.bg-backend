package bg.deck.common.exception;

import static bg.deck.common.constant.ExceptionConstants.INVALID_LINK;

/**
 * A link sent by email — password reset, for one — that is unknown, already
 * used or expired.
 *
 * <p>Separate from {@link InvalidTokenException} on purpose. That one means
 * "your credentials are not valid" and answers 401, which the client reads as
 * an expired session: it tried to refresh, failed, and dropped the person on
 * the login screen instead of the invalid-link page. This is a bad request
 * about the link, not about who is asking, and answers 400.
 */
public class InvalidLinkException extends RuntimeException {
    public InvalidLinkException() {
        super(INVALID_LINK);
    }
}
