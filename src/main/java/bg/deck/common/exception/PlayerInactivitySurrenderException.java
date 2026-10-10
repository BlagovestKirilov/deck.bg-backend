package bg.deck.common.exception;

import bg.deck.common.constant.ExceptionConstants;

public class PlayerInactivitySurrenderException extends RuntimeException {
    public PlayerInactivitySurrenderException() {
        super(ExceptionConstants.PLAYER_SURRENDERED_DUE_TO_INACTIVITY);
    }
}
