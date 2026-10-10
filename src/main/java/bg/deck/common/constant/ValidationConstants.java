package bg.deck.common.constant;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ValidationConstants {
    public static final int USERNAME_MIN = 4;
    public static final int USERNAME_MAX = 20;
    public static final int PASSWORD_MIN = 5;
    public static final int PASSWORD_MAX = 50;

    public static final String ALPHANUMERIC_PATTERN = "^[A-Za-z0-9]+$";
    public static final String PASSWORD_PATTERN = "^[A-Za-z0-9!@#$%^&*(){}\\[\\]<>_+=\\-.,?|~`]+$";

    public static final String USERNAME_EMPTY = "Username cannot be empty";
    public static final String USERNAME_SIZE = "Username must be between " + USERNAME_MIN + " and " + USERNAME_MAX + " characters";
    public static final String USERNAME_PATTERN = "Username can contain only letters and digits";

    public static final String PASSWORD_EMPTY = "Password cannot be empty";
    public static final String PASSWORD_SIZE = "Password must be between " + PASSWORD_MIN + " and " + PASSWORD_MAX + " characters";
    public static final String PASSWORD_PATTERN_MSG = "Password can contain only letters, digits, and special symbols";

    public static final String REFRESH_TOKEN_EMPTY = "refreshToken cannot be empty";
    public static final String CARD_ID_NULL = "cardId must not be null";

    public static final String EMAIL_EMPTY = "Email cannot be empty";
    public static final String EMAIL_INVALID = "Email format is invalid";

    public static final String TOKEN_NULL = "Token must not be null";
}
