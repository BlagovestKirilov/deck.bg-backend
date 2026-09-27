package bg.deck.constant;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ExceptionConstants {

    /**
     * Said to a player whose move arrived after the table moved on. The same
     * words as the optimistic-lock answer, because it is the same thing from
     * the player's side: what they tapped is no longer there to tap.
     */
    public static final String MOVE_NO_LONGER_LEGAL = "\u0425\u043e\u0434\u044a\u0442 \u0432\u0435\u0447\u0435 \u043d\u0435 \u0435 \u0432\u044a\u0437\u043c\u043e\u0436\u0435\u043d.";
    public static final String INVALID_CREDENTIALS = "Invalid credentials provided for username %s.";
    public static final String INVALID_TOKEN = "Invalid token.";
    public static final String INVALID_LINK = "Invalid or expired link.";
    public static final String USERNAME_ALREADY_EXISTS = "Username '%s' already exists.";
    public static final String EMAIL_ALREADY_EXISTS = "Email '%s' already exists.";
    public static final String INCORRECT_CREDENTIALS_MESSAGE = "Username or password is incorrect.";
    public static final String VALIDATION_ERROR_TITLE = "Validation Error.";
    public static final String INTERNAL_SERVER_ERROR_MESSAGE = "An unexpected error occurred.";
    public static final String LOG_FORMAT_SECURITY = "{} | IP: {} | Path: {}.";
    public static final String LOG_FORMAT_ERROR = "ERROR: {} | Path: {} | Details: {}.";
    public static final String LOG_FORMAT_UNHANDLED = "UNHANDLED EXCEPTION: IP: {} | Path: {} | {} at {}.";
    public static final String COMMA_DELIMITER = ", ";
    public static final String VALIDATION_DETAILS_FORMAT = "%s: %s";
    public static final String USER_NOT_PART_OF_GAME = "User: %s is not part of this game.";
    public static final String PLAYER_NOT_IN_TURN = "Player: %s is not in turn.";
    public static final String PLAYER_NOT_FIRST_IN_TURN = "Player: %s is not first in turn.";
    public static final String CARD_NOT_FOUND = "Card not found in player %s hand.";
    public static final String CARD_NOT_FOUND_FOR_REPLACING = "Card not found for replacing for player %s.";
    public static final String NO_ACTIVE_GAME = "No active game found for %s.";
    public static final String DECK_SIZE_EXCEPTION = "Deck size must be greater than %s and less than %s.";
    public static final String CARD_NOT_PLAYABLE = "Card not playable.";
    public static final String EMAIL_CONFIRMATION_NOT_FOUND = "Email confirmation with token %s does not found.";
    public static final String FAILED_SENDING_EMAIL = "Failed to send confirmation email to %s";
    public static final String USER_NOT_FOUND = "User not found: %s";
    public static final String SAME_PASSWORD = "New password must be different.";
    public static final String EMAIL_NOT_CONFIRMED = "Email %s is not confirmed.";
    public static final String PLAYER_SURRENDERED_DUE_TO_INACTIVITY = "Player surrendered due to repeated inactivity.";
    public static final String TYPE_MISMATCH_TITLE = "Invalid parameter";
    public static final String PARAMETER_TYPE_MISMATCH = "Parameter '%s' must be of type %s";
    public static final String UNKNOWN = "unknown";
}
